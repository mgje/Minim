/*
 *  Copyright (c) 2007 - 2008 by Damien Di Fede <ddf@compartmental.net>
 *
 *   This program is free software; you can redistribute it and/or modify
 *   it under the terms of the GNU Library General Public License as published
 *   by the Free Software Foundation; either version 2 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Library General Public License for more details.
 *
 *   You should have received a copy of the GNU Library General Public
 *   License along with this program; if not, write to the Free Software
 *   Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

package ddf.minim.javasound;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

import javazoom.spi.mpeg.sampled.file.MpegAudioFormat;

import org.tritonus.share.sampled.AudioUtils;
import org.tritonus.share.sampled.FloatSampleBuffer;
import org.tritonus.share.sampled.file.TAudioFileFormat;

import processing.core.PApplet;
import ddf.minim.AudioMetaData;
import ddf.minim.AudioSample;
import ddf.minim.Minim;
import ddf.minim.Recordable;
import ddf.minim.spi.AudioRecording;
import ddf.minim.spi.AudioRecordingStream;
import ddf.minim.spi.AudioStream;
import ddf.minim.spi.AudioSynthesizer;
import ddf.minim.spi.MinimServiceProvider;
import ddf.minim.spi.SampleRecorder;

/**
 * JSMinim is an implementation of the {@link MinimServiceProvider} interface that use
 * Javasound to provide all audio functionality. That's about all you really need to know about it.
 * 
 * @author Damien Di Fede
 *
 */

public class JSMinim implements MinimServiceProvider
{
	private boolean debug;
  private PApplet app;

	public JSMinim(PApplet parent)
	{
		debug = false;
    app = parent;
	}

	public void start()
	{
	}

	public void stop()
	{
	}
	
	public void debugOn()
	{
		debug = true;
	}
	
	public void debugOff()
	{
		debug = false;
	}
	
	void debug(String s)
	{
		if ( debug )
		{
			PApplet.println("==== JavaSound Minim Debug ====");
			String[] lines = s.split("\n");
			for(int i = 0; i < lines.length; i++)
			{
				PApplet.println("==== " + lines[i]);
			}
			PApplet.println();
		}
	}
	
	void error(String s)
	{
		PApplet.println("==== JavaSound Minim Error ====");
		String[] lines = s.split("\n");
		for(int i = 0; i < lines.length; i++)
		{
			PApplet.println("==== " + lines[i]);
		}
		PApplet.println();
	}

	public SampleRecorder getSampleRecorder(Recordable source, String fileName,
			boolean buffered)
	{
		String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
		debug("createRecorder: file extension is " + ext + ".");
		AudioFileFormat.Type fileType = null;
		if (ext.equals(Minim.WAV.getExtension()))
		{
			fileType = Minim.WAV;
		}
		else if (ext.equals(Minim.AIFF.getExtension()) || ext.equals("aif"))
		{
			fileType = Minim.AIFF;
		}
		else if (ext.equals(Minim.AIFC.getExtension()))
		{
			fileType = Minim.AIFC;
		}
		else if (ext.equals(Minim.AU.getExtension()))
		{
			fileType = Minim.AU;
		}
		else if (ext.equals(Minim.SND.getExtension()))
		{
			fileType = Minim.SND;
		}
		else
		{
			error("The extension " + ext + " is not a recognized audio file type.");
			return null;
		}
		SampleRecorder recorder = null;
		if (buffered)
		{
			recorder = new JSBufferedSampleRecorder(this,
                                  app.sketchPath(fileName),
																	fileType,
																	source.getFormat(),
																	source.bufferSize());
		}
		else
		{
			recorder = new JSStreamingSampleRecorder(this,
                                  app.sketchPath(fileName),
																	fileType,
																	source.getFormat(),
																	source.bufferSize());
		}
		return recorder;
	}

	public AudioRecordingStream getAudioRecordingStream(String filename,
			int bufferSize)
	{
		AudioRecordingStream mstream = null;
		AudioInputStream ais = getAudioInputStream(filename);
		debug("Reading from " + ais.getClass().toString());
		if (ais != null)
		{
			debug("File format is: " + ais.getFormat().toString());
			AudioFormat format = ais.getFormat();
			// special handling for mp3 files because
			// they need to be converted to PCM
			if (format instanceof MpegAudioFormat)
			{
				AudioFormat baseFormat = format;
				format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
													baseFormat.getSampleRate(), 16,
													baseFormat.getChannels(),
													baseFormat.getChannels() * 2,
													baseFormat.getSampleRate(), false);
				// converts the stream to PCM audio from mp3 audio
				AudioInputStream decAis = getAudioInputStream(format, ais);
				// source data line is for sending the file audio out to the
				// speakers
				SourceDataLine line = getSourceDataLine(format, bufferSize);
				if (decAis != null && line != null)
				{
					Map props = getID3Tags(filename);
					long lengthInMillis = -1;
					if (props.containsKey("duration"))
					{
						Long dur = (Long)props.get("duration");
		            if ( dur.longValue() > 0 )
		            {
		              lengthInMillis = dur.longValue() / 1000;
		            }
					}
					MP3MetaData meta = new MP3MetaData(filename, lengthInMillis, props);
					mstream = new JSMPEGAudioRecordingStream(this, meta, ais,	decAis, line, bufferSize);
				}
			} // format instanceof MpegAudioFormat
			else
			{
				// source data line is for sending the file audio out to the
				// speakers
				SourceDataLine line = getSourceDataLine(format, bufferSize);
				if (line != null)
				{
					long length = AudioUtils.frames2Millis(ais.getFrameLength(), format);
					BasicMetaData meta = new BasicMetaData(filename, length);
					mstream = new JSPCMAudioRecordingStream(this, meta, ais, line, bufferSize);
				}
			} // else
		} // ais != null
		return mstream;
	}

	private Map getID3Tags(String filename)
	{
		debug("Getting the properties.");
		Map props = new HashMap();
		try
		{
			MpegAudioFileReader reader = new MpegAudioFileReader(this);
			InputStream stream = app.createInput(filename);
			AudioFileFormat baseFileFormat = reader.getAudioFileFormat(
																							stream,
																							stream.available());
			stream.close();
			if (baseFileFormat instanceof TAudioFileFormat)
			{
				TAudioFileFormat fileFormat = (TAudioFileFormat)baseFileFormat;
				props = fileFormat.properties();
				if (props.size() == 0)
				{
					error("No file properties available for " + filename + ".");
				}
				else
				{
					debug("File properties: " + props.toString());
				}
			}
		}
		catch (UnsupportedAudioFileException e)
		{
			error("Couldn't get the file format for " + filename + ": "
					+ e.getMessage());
		}
		catch (IOException e)
		{
			error("Couldn't access " + filename + ": " + e.getMessage());
		}
		return props;
	}

	public AudioStream getAudioStream(int type, int bufferSize,
			float sampleRate, int bitDepth)
	{
		if (bitDepth != 8 && bitDepth != 16)
			throw new IllegalArgumentException(
															"Unsupported bit depth, use either 8 or 16.");
		AudioFormat format = new AudioFormat(sampleRate, bitDepth, type, true,
															false);
		TargetDataLine line = getTargetDataLine(format, bufferSize * 4);
		if (line != null)
		{
			return new JSAudioStream(line, bufferSize);
		}
		return null;
	}

	public AudioSample getAudioSample(String filename, int bufferSize)
	{
		AudioInputStream ais = getAudioInputStream(filename);
		if (ais != null)
		{
			AudioMetaData meta = null;
			AudioFormat format = ais.getFormat();
			FloatSampleBuffer samples = null;
			if (format instanceof MpegAudioFormat)
			{
				AudioFormat baseFormat = format;
				format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
													baseFormat.getSampleRate(), 16,
													baseFormat.getChannels(),
													baseFormat.getChannels() * 2,
													baseFormat.getSampleRate(), false);
				// converts the stream to PCM audio from mp3 audio
				ais = getAudioInputStream(format, ais);
				// get a map of properties so we can find out how long it is
				Map props = getID3Tags(filename);
				// there is a property called mp3.length.bytes, but that is
				// the length in bytes of the mp3 file, which will of course
				// be much shorter than the decoded version. so we use the
				// duration of the file to figure out how many bytes the
				// decoded file will be.
				long dur = ((Long)props.get("duration")).longValue();
				int toRead = (int)AudioUtils.millis2Bytes(dur / 1000, format);
				samples = loadFloatAudio(ais, toRead);
				meta = new MP3MetaData(filename, dur / 1000, props);
			}
			else
			{
				samples = loadFloatAudio(ais, (int)ais.getFrameLength() * format.getFrameSize());
				long length = AudioUtils.frames2Millis(samples.getSampleCount(), format);
				meta = new BasicMetaData(filename, length);
			}
			AudioSynthesizer out = getAudioSynthesizer(format.getChannels(), 
			                                             bufferSize, 
			                                             format.getSampleRate(), 
			                                             format.getSampleSizeInBits());
			if (out != null)
			{
				SampleSignal ssig = new SampleSignal(samples);
				out.setAudioSignal(ssig);
				return new JSAudioSample(meta, ssig, out);
			}
			else
			{
				error("Couldn't acquire an output.");
			}
		}
		return null;
	}

	public AudioSynthesizer getAudioSynthesizer(int type, int bufferSize,
			float sampleRate, int bitDepth)
	{
		if (bitDepth != 8 && bitDepth != 16)
		{
			throw new IllegalArgumentException("Unsupported bit depth, use either 8 or 16.");
		}
		AudioFormat format = new AudioFormat(sampleRate, bitDepth, type, true, false);
		SourceDataLine sdl = getSourceDataLine(format, bufferSize);
		if (sdl != null)
		{
			return new JSAudioSynthesizer(sdl, bufferSize);
		}
		return null;
	}

	public AudioRecording getAudioRecordingClip(String filename)
	{
		Clip clip = null;
		AudioMetaData meta = null;
		AudioInputStream ais = getAudioInputStream(filename);
		if (ais != null)
		{
			AudioFormat format = ais.getFormat();
			if (format instanceof MpegAudioFormat)
			{
				AudioFormat baseFormat = format;
				format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
													baseFormat.getSampleRate(), 16,
													baseFormat.getChannels(),
													baseFormat.getChannels() * 2,
													baseFormat.getSampleRate(), false);
				// converts the stream to PCM audio from mp3 audio
				ais = getAudioInputStream(format, ais);
			}
			DataLine.Info info = new DataLine.Info(Clip.class, ais.getFormat());
			if (AudioSystem.isLineSupported(info))
			{
				// Obtain and open the line.
				try
				{
					clip = (Clip)AudioSystem.getLine(info);
					clip.open(ais);
				}
				catch (Exception e)
				{
					error("Error obtaining Javasound Clip: " + e.getMessage());
					return null;
				}
				Map props = getID3Tags(filename);
				long lengthInMillis = -1;
				if (props.containsKey("duration"))
				{
					Long dur = (Long)props.get("duration");
					lengthInMillis = dur.longValue() / 1000;
				}
				meta = new MP3MetaData(filename, lengthInMillis, props);
			}
			else
			{
				error("File format not supported.");
				return null;
			}
		}
		if (meta == null)
		{
			// this means we're dealing with not-an-mp3
			meta = new BasicMetaData(filename, clip.getMicrosecondLength() / 1000);
		}
		return new JSAudioRecordingClip(clip, meta);
	}
	
	public AudioRecording getAudioRecording(String filename)
	{
		AudioMetaData meta = null;
		AudioInputStream ais = getAudioInputStream(filename);
		byte[] samples;
		if (ais != null)
		{
			AudioFormat format = ais.getFormat();
			if (format instanceof MpegAudioFormat)
			{
				AudioFormat baseFormat = format;
				format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
													baseFormat.getSampleRate(), 16,
													baseFormat.getChannels(),
													baseFormat.getChannels() * 2,
													baseFormat.getSampleRate(), false);
				// converts the stream to PCM audio from mp3 audio
				ais = getAudioInputStream(format, ais);
				//	 get a map of properties so we can find out how long it is
				Map props = getID3Tags(filename);
				// there is a property called mp3.length.bytes, but that is
				// the length in bytes of the mp3 file, which will of course
				// be much shorter than the decoded version. so we use the
				// duration of the file to figure out how many bytes the
				// decoded file will be.
				long dur = ((Long)props.get("duration")).longValue();
				int toRead = (int)AudioUtils.millis2Bytes(dur / 1000, format);
				samples = loadByteAudio(ais, toRead);
				meta = new MP3MetaData(filename, dur / 1000, props);
			}
			else
			{
				samples = loadByteAudio(ais, (int)ais.getFrameLength() * format.getFrameSize());
				long length = AudioUtils.bytes2Millis(samples.length, format);
				meta = new BasicMetaData(filename, length);
			}
			SourceDataLine line = getSourceDataLine(format, 2048);
			if ( line != null )
			{
				return new JSAudioRecording(this, samples, line, meta);
			}
		}
		return null;
	}
	
	FloatSampleBuffer loadFloatAudio(AudioInputStream ais, int toRead)
	{
		FloatSampleBuffer samples = new FloatSampleBuffer();
		int totalRead = 0;
		byte[] rawBytes = new byte[toRead];
		try
		{
			// we have to read in chunks because the decoded stream won't
			// read more than about 2000 bytes at a time
			while (totalRead < toRead)
			{
				int actualRead = ais.read(rawBytes, totalRead, toRead
						- totalRead);
				if (actualRead < 1)
					break;
				totalRead += actualRead;
			}
			ais.close();
		}
		catch (Exception ioe)
		{
			error("Error loading file into memory: " + ioe.getMessage());
		}
		debug("Needed to read " + toRead + " actually read " + totalRead);
		samples.initFromByteArray(rawBytes, 0, totalRead, ais.getFormat());
		return samples;
	}
	
	byte[] loadByteAudio(AudioInputStream ais, int toRead)
	{
		int totalRead = 0;
		byte[] rawBytes = new byte[toRead];
		try
		{
			// we have to read in chunks because the decoded stream won't
			// read more than about 2000 bytes at a time
			while (totalRead < toRead)
			{
				int actualRead = ais.read(rawBytes, totalRead, toRead	- totalRead);
				if (actualRead < 1)
					break;
				totalRead += actualRead;
			}
			ais.close();
		}
		catch (Exception ioe)
		{
			error("Error loading file into memory: " + ioe.getMessage());
		}
		debug("Needed to read " + toRead + " actually read " + totalRead);
		return rawBytes;
	}

  /**
   * 
   * @param filename the 
   * @param is
   * @return
   */
	AudioInputStream getAudioInputStream(String filename)
	{
		AudioInputStream ais = null;
		BufferedInputStream bis = null;
		if (filename.startsWith("http"))
		{
			try
			{
				ais = getAudioInputStream(new URL(filename));
			}
			catch (MalformedURLException e)
			{
				error("Bad URL: " + e.getMessage());
			}
			catch (UnsupportedAudioFileException e)
			{
				error("URL is in an unsupported audio file format: " + e.getMessage());
			}
			catch (IOException e)
			{
				Minim.error("Error reading the URL: " + e.getMessage());
			}
		}
		else
		{
			try
			{
        InputStream is = app.createInput(filename);
				debug("Base input stream is: " + is.toString());
				bis = new BufferedInputStream(is);
				ais = getAudioInputStream(bis);
        // don't mark it like this because it means the entire
        // file will be loaded into memory as it plays. this 
        // will cause out-of-memory problems with very large files.
				// ais.mark((int)ais.available());
				debug("Acquired AudioInputStream.\n" + "It is "
						+ ais.getFrameLength() + " frames long.\n"
						+ "Marking support: " + ais.markSupported());
			}
			catch (IOException ioe)
			{
				error("IOException: " + ioe.getMessage());
			}
			catch (UnsupportedAudioFileException uafe)
			{
				error("Unsupported Audio File: " + uafe.getMessage());
			}
		}
		return ais;
	}

	/**
	 * This method is also part of AppletMpegSPIWorkaround, which uses yet
	 * another workaround to load an internet radio stream.
	 * 
	 * @param url
	 *           the URL of the stream
	 * @return an AudioInputStream of the streaming audio
	 * @throws UnsupportedAudioFileException
	 * @throws IOException
	 */
	AudioInputStream getAudioInputStream(URL url)
			throws UnsupportedAudioFileException, IOException
	{

		// alexey fix: we use MpegAudioFileReaderWorkaround with URL and user
		// agent
		return new MpegAudioFileReaderWorkaround(this).getAudioInputStream(url, null);
	}

	/**
	 * This method is a replacement for
	 * AudioSystem.getAudioInputStream(InputStream), which includes workaround
	 * for getting an mp3 AudioInputStream when sketch is running in an applet.
	 * The workaround was developed by the Tritonus team and originally comes
	 * from the package javazoom.jlgui.basicplayer
	 * 
	 * @param is
	 *           The stream to convert to an AudioInputStream
	 * @return an AudioInputStream that will read from is
	 * @throws UnsupportedAudioFileException
	 * @throws IOException
	 */
	AudioInputStream getAudioInputStream(InputStream is)
			throws UnsupportedAudioFileException, IOException
	{
		try
		{
			return AudioSystem.getAudioInputStream(is);
		}
		catch (Exception iae)
		{
			debug("Using AppletMpegSPIWorkaround to get codec");
			return new MpegAudioFileReader(this).getAudioInputStream(is);
		}
	}

	/**
	 * This method is a replacement for
	 * AudioSystem.getAudioInputStream(AudioFormat, AudioInputStream), which is
	 * used for audio format conversion at the stream level. This method includes
	 * a workaround for converting from an mp3 AudioInputStream when the sketch
	 * is running in an applet. The workaround was developed by the Tritonus team
	 * and originally comes from the package javazoom.jlgui.basicplayer
	 * 
	 * @param targetFormat
	 *           the AudioFormat to convert the stream to
	 * @param sourceStream
	 *           the stream containing the unconverted audio
	 * @return an AudioInputStream in the target format
	 */
	AudioInputStream getAudioInputStream(AudioFormat targetFormat,
			AudioInputStream sourceStream)
	{
		try
		{
			return AudioSystem.getAudioInputStream(targetFormat, sourceStream);
		}
		catch (IllegalArgumentException iae)
		{
			debug("Using AppletMpegSPIWorkaround to get codec");
			try
			{
				Class.forName("javazoom.spi.mpeg.sampled.convert.MpegFormatConversionProvider");
				return new javazoom.spi.mpeg.sampled.convert.MpegFormatConversionProvider().getAudioInputStream(
																																				targetFormat,
																																				sourceStream);
			}
			catch (ClassNotFoundException cnfe)
			{
				throw new IllegalArgumentException("Mpeg codec not properly installed");
			}
		}
	}

	SourceDataLine getSourceDataLine(AudioFormat format, int bufferSize)
	{
		SourceDataLine line = null;
		DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
		if ( AudioSystem.isLineSupported(info) ) 
		{
			try
			{
				line = (SourceDataLine)AudioSystem.getLine(info);
				// remember that time you spent, like, an entire afternoon fussing
				// with this buffer size to try to get the latency decent on Linux?
				// Yah, don't fuss with this anymore, ok?
				line.open(format, bufferSize * format.getFrameSize() * 4);
				if ( line.isOpen() )
				{
					debug("SourceDataLine is " + line.getClass().toString() + "\n"
					      + "Buffer size is " + line.getBufferSize() + " bytes.\n" 
					      + "Format is "	+ line.getFormat().toString() + ".");
					return line;
				}
			}
			catch (LineUnavailableException e)
			{
				error("Couldn't open the line: " + e.getMessage());
			}
		}
		error("Unable to return a SourceDataLine: unsupported format - " + format.toString());
		return line;
	}

	TargetDataLine getTargetDataLine(AudioFormat format, int bufferSize)
	{
		TargetDataLine line = null;
		DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
    //Mixer.Info[] infos = AudioSystem.getMixerInfo();
    //for(int i = 0; i < infos.length; i++)
    //{
      //Minim.debug(infos[i].toString());
    //}
    // TODO: Ok, so this is nasty. In Windows, with the MOTU turned on
    // this particular mixer represents the Mix1, which means I can use it
    // to monitor the computer's audio output. However, I have no way 
    // of asking for this specifically, which means if I use currently
    // shipped code I get nothing when I try to grab the input.
    // Or, rather, I get an input and who knows what it's connected to.
    // I hate to open up the idea of Mixers to the user, but if people 
    // know there is a specific mixer they want an input from, they should
    // be able to specify it somehow...
    //Mixer mix = AudioSystem.getMixer(infos[11]);
    //Minim.debug(mix.getMixerInfo().toString());
		if (AudioSystem.isLineSupported(info))
		{
			try
			{
				line = (TargetDataLine)AudioSystem.getLine(info);
        //line = (TargetDataLine)mix.getLine(info);
				line.open(format, bufferSize * format.getFrameSize());
				debug("TargetDataLine buffer size is " + line.getBufferSize()
						+ "\n" + "TargetDataLine format is "
						+ line.getFormat().toString() + "\n"
						+ "TargetDataLine info is " + line.getLineInfo().toString());
			}
			catch (Exception e)
			{
				error("Error acquiring TargetDataLine: " + e.getMessage());
			}
		}
		else
		{
			error("Unable to return a TargetDataLine: unsupported format - " + format.toString());
		}
		return line;
	}

}