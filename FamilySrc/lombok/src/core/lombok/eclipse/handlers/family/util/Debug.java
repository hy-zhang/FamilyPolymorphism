package lombok.eclipse.handlers.family.util;

import java.io.BufferedWriter;
import java.io.FileWriter;

/**
 * @author Haoyuan
 * This class is used for debugging.
 * By initializing the file path, users can write messages into the file
 * during annotation processing.
 */
public class Debug {
	
	String file = "";
	BufferedWriter bw;
	
	/**
	 * Constructor.
	 * @param file: the file path for logging
	 */
	public Debug(String file) {
		this.file = file;
	}
	
	/**
	 * Logging messages.
	 * @param msg: message
	 * @param append: append
	 */
	public void log(String msg, boolean append) {
		try {
			bw = new BufferedWriter(new FileWriter(file, append));
			bw.write(msg);
			bw.close();
		} catch (Exception e) { e.printStackTrace(); }
	}
	
	/**
	 * Clear content in the file.
	 */
	public void clear() {
		try {
			bw = new BufferedWriter(new FileWriter(file));
			bw.write("");
			bw.close();
		} catch (Exception e) { e.printStackTrace(); }
	}
	
}
