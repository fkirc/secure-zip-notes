/*
* Copyright 2010 Srikanth Reddy Lingala  
* 
* Licensed under the Apache License, Version 2.0 (the "License"); 
* you may not use this file except in compliance with the License. 
* You may obtain a copy of the License at 
* 
* http://www.apache.org/licenses/LICENSE-2.0 
* 
* Unless required by applicable law or agreed to in writing, 
* software distributed under the License is distributed on an "AS IS" BASIS, 
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
* See the License for the specific language governing permissions and 
* limitations under the License. 
*/

package com.ditronic.securezipnotes.zip4j.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import com.ditronic.securezipnotes.zip4j.exception.ZipException;
import com.ditronic.securezipnotes.zip4j.exception.ZipExceptionConstants;
import com.ditronic.securezipnotes.zip4j.io.ZipInputStream;
import com.ditronic.securezipnotes.zip4j.model.FileHeader;
import com.ditronic.securezipnotes.zip4j.model.ZipModel;
import com.ditronic.securezipnotes.zip4j.model.ZipParameters;
import com.ditronic.securezipnotes.zip4j.progress.ProgressMonitor;
import com.ditronic.securezipnotes.zip4j.unzip.Unzip;
import com.ditronic.securezipnotes.zip4j.util.ArchiveMaintainer;
import com.ditronic.securezipnotes.zip4j.util.InternalZipConstants;
import com.ditronic.securezipnotes.zip4j.util.Zip4jUtil;
import com.ditronic.securezipnotes.zip4j.zip.ZipEngine;

/**
 * Base class to handle zip files. Some of the operations supported
 * in this class are:<br>
 * <ul>
 * 		<li>Create Zip File</li>
 * 		<li>Add files to zip file</li>
 * 		<li>Add folder to zip file</li>
 * 		<li>Extract files from zip files</li>
 * 		<li>Remove files from zip file</li>
 * </ul>
 *
 */

public class ZipFile {
	
	private String file;
	private int mode;
	private ZipModel zipModel;
	private boolean isEncrypted;
	private ProgressMonitor progressMonitor;
	private boolean runInThread;
	private String fileNameCharset;
	
	/**
	 * Creates a new Zip File Object with the given zip file path.
	 * If the zip file does not exist, it is not created at this point. 
	 * @param zipFile
	 * @throws ZipException
	 */
	public ZipFile(String zipFile) throws ZipException {
		this(new File(zipFile));
	}
	
	/**
	 * Creates a new Zip File Object with the input file.
	 * If the zip file does not exist, it is not created at this point.
	 * @param zipFile
	 * @throws ZipException
	 */
	public ZipFile(File zipFile) throws ZipException {
		if (zipFile == null) {
			throw new ZipException("Input zip file parameter is not null", 
					ZipExceptionConstants.inputZipParamIsNull);
		}
		
		this.file = zipFile.getPath();
		this.mode = InternalZipConstants.MODE_UNZIP;
		this.progressMonitor = new ProgressMonitor();
		this.runInThread = false;
	}
	
	/**
	 * Creates a new entry in the zip file and adds the content of the inputstream to the
	 * zip file. ZipParameters.isSourceExternalStream and ZipParameters.fileNameInZip have to be
	 * set before in the input parameters. If the file name ends with / or \, this method treats the
	 * content as a directory. Setting the flag ProgressMonitor.setRunInThread to true will have
	 * no effect for this method and hence this method cannot be used to add content to zip in
	 * thread mode
	 * @param inputStream
	 * @param parameters
	 * @throws ZipException
	 */
	public void addStream(InputStream inputStream, ZipParameters parameters) throws ZipException {
		if (inputStream == null) {
			throw new ZipException("inputstream is null, cannot add file to zip");
		}
		
		if (parameters == null) {
			throw new ZipException("zip parameters are null");
		}
		
		this.setRunInThread(false);
		
		checkZipModel();
		
		if (this.zipModel == null) {
			throw new ZipException("internal error: zip model is null");
		}
		
		if (Zip4jUtil.checkFileExists(file)) {
			if (zipModel.isSplitArchive()) {
				throw new ZipException("Zip file already exists. Zip file format does not allow updating split/spanned files");
			}
		}
		
		ZipEngine zipEngine = new ZipEngine(zipModel);
		zipEngine.addStreamToZip(inputStream, parameters);
	}
	
	/**
	 * Reads the zip header information for this zip file. If the zip file
	 * does not exist, then this method throws an exception.<br><br>
	 * <b>Note:</b> This method does not read local file header information
	 * @throws ZipException
	 */
	public void readZipInfo() throws ZipException {
		
		if (!Zip4jUtil.checkFileExists(file)) {
			throw new ZipException("zip file does not exist");
		}
		
		if (!Zip4jUtil.checkFileReadAccess(this.file)) {
			throw new ZipException("no read access for the input zip file");
		}
		
		if (this.mode != InternalZipConstants.MODE_UNZIP) {
			throw new ZipException("Invalid mode");
		}
		
		RandomAccessFile raf = null;
		try {
			raf = new RandomAccessFile(new File(file), InternalZipConstants.READ_MODE);
			
			if (zipModel == null) {
				
				HeaderReader headerReader = new HeaderReader(raf);
				zipModel = headerReader.readAllHeaders(this.fileNameCharset);
				if (zipModel != null) {
					zipModel.setZipFile(file);
				}
			}
		} catch (FileNotFoundException e) {
			throw new ZipException(e);
		} finally {
			if (raf != null) {
				try {
					raf.close();
				} catch (IOException e) {
					//ignore
				}
			}
		}
	}

	
	/**
	 * Extracts a specific file from the zip file to the destination path.
	 * If destination path is invalid, then this method throws an exception.
	 * @param fileHeader
	 * @throws ZipException
	 */
	public ByteArrayOutputStream extractFile(FileHeader fileHeader) {
		
		if (fileHeader == null) {
			throw new RuntimeException("input file header is null, cannot extract file");
		}
		
		if (progressMonitor.getState() == ProgressMonitor.STATE_BUSY) {
			throw new RuntimeException("invalid operation - Zip4j is in busy state");
		}
		try {
			return fileHeader.extractFile(zipModel, null, progressMonitor);
		} catch (ZipException e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Sets the password for the zip file.<br>
	 * <b>Note</b>: For security reasons, usage of this method is discouraged. Use 
	 * setPassword(char[]) instead. As strings are immutable, they cannot be wiped
	 * out from memory explicitly after usage. Therefore, usage of Strings to store 
	 * passwords is discouraged. More info here: 
	 * http://docs.oracle.com/javase/1.5.0/docs/guide/security/jce/JCERefGuide.html#PBEEx
	 * @param password
	 * @throws ZipException
	 */
	public void setPassword(String password) throws ZipException {
		if (!Zip4jUtil.isStringNotNullAndNotEmpty(password)) {
			throw new NullPointerException();
		}
		setPassword(password.toCharArray());
	}
	
	/**
	 * Sets the password for the zip file
	 * @param password
	 * @throws ZipException
	 */
	public void setPassword(char[] password) throws ZipException {
		if (zipModel == null) {
			readZipInfo();
			if (zipModel == null) {
				throw new ZipException("Zip Model is null");
			}
		}
		
		if (zipModel.getCentralDirectory() == null || zipModel.getCentralDirectory().getFileHeaders() == null) {
			throw new ZipException("invalid zip file");
		}
		
		for (int i = 0; i < zipModel.getCentralDirectory().getFileHeaders().size(); i++) {
			if (zipModel.getCentralDirectory().getFileHeaders().get(i) != null) {
				if (((FileHeader)zipModel.getCentralDirectory().getFileHeaders().get(i)).isEncrypted()) {
					((FileHeader)zipModel.getCentralDirectory().getFileHeaders().get(i)).setPassword(password);
				}
			}
		}
	}

	public List getFileHeadersFast() {
		// Skip reading file headers
		if (zipModel == null || zipModel.getCentralDirectory() == null) {
			return null;
		}
		return zipModel.getCentralDirectory().getFileHeaders();
	}

	public FileHeader getFileHeader(String fileName) throws ZipException {
		if (!Zip4jUtil.isStringNotNullAndNotEmpty(fileName)) {
			throw new ZipException("input file name is emtpy or null, cannot get FileHeader");
		}
		
		if (zipModel == null || zipModel.getCentralDirectory() == null) {
			return null;
		}
		
		return Zip4jUtil.getFileHeader(zipModel, fileName);
	}
	
	/**
	 * Checks to see if the zip file is encrypted
	 * @return true if encrypted, false if not
	 * @throws ZipException
	 */
	public boolean isEncrypted() throws ZipException {
		if (zipModel == null) {
			readZipInfo();
			if (zipModel == null) {
				throw new ZipException("Zip Model is null");
			}
		}
		
		if (zipModel.getCentralDirectory() == null || zipModel.getCentralDirectory().getFileHeaders() == null) {
			throw new ZipException("invalid zip file");
		}
		
		ArrayList fileHeaderList = zipModel.getCentralDirectory().getFileHeaders();
		for (int i = 0; i < fileHeaderList.size(); i++) {
			FileHeader fileHeader = (FileHeader)fileHeaderList.get(i);
			if (fileHeader != null) {
				if (fileHeader.isEncrypted()) {
					isEncrypted = true;
					break;
				}
			}
		}
		
		return isEncrypted;
	}
	
	/**
	 * Checks if the zip file is a split archive
	 * @return true if split archive, false if not
	 * @throws ZipException
	 */
	public boolean isSplitArchive() throws ZipException {

		if (zipModel == null) {
			readZipInfo();
			if (zipModel == null) {
				throw new ZipException("Zip Model is null");
			}
		}
		
		return zipModel.isSplitArchive();
	
	}
	
	/**
	 * Removes the file provided in the input paramters from the zip file.
	 * This method first finds the file header and then removes the file.
	 * If file does not exist, then this method throws an exception.
	 * If zip file is a split zip file, then this method throws an exception as
	 * zip specification does not allow for updating split zip archives.
	 * @param fileName
	 * @throws ZipException
	 */
	public void removeFile(String fileName) throws ZipException {
		
		if (!Zip4jUtil.isStringNotNullAndNotEmpty(fileName)) {
			throw new ZipException("file name is empty or null, cannot remove file");
		}
		
		if (zipModel == null) {
			if (Zip4jUtil.checkFileExists(file)) {
				readZipInfo();
			}
		}
		
		if (zipModel.isSplitArchive()) {
			throw new ZipException("Zip file format does not allow updating split/spanned files");
		}
		
		FileHeader fileHeader = Zip4jUtil.getFileHeader(zipModel, fileName);
		if (fileHeader == null) {
			throw new ZipException("could not find file header for file: " + fileName);
		}
		
		removeFile(fileHeader);
	}
	
	/**
	 * Removes the file provided in the input file header from the zip file.
	 * If zip file is a split zip file, then this method throws an exception as
	 * zip specification does not allow for updating split zip archives.
	 * @param fileHeader
	 * @throws ZipException
	 */
	public void removeFile(FileHeader fileHeader) throws ZipException {
		if (fileHeader == null) {
			throw new ZipException("file header is null, cannot remove file");
		}
		
		if (zipModel == null) {
			if (Zip4jUtil.checkFileExists(file)) {
				readZipInfo();
			}
		}
		
		if (zipModel.isSplitArchive()) {
			throw new ZipException("Zip file format does not allow updating split/spanned files");
		}
		
		ArchiveMaintainer archiveMaintainer = new ArchiveMaintainer();
		archiveMaintainer.initProgressMonitorForRemoveOp(zipModel, fileHeader, progressMonitor);
		archiveMaintainer.removeZipFile(zipModel, fileHeader, progressMonitor, runInThread);
	}
	
	/**
	 * Sets comment for the Zip file
	 * @param comment
	 * @throws ZipException
	 */
	public void setComment(String comment) throws ZipException {
		if (comment == null) {
			throw new ZipException("input comment is null, cannot update zip file");
		}
		
		if (!Zip4jUtil.checkFileExists(file)) {
			throw new ZipException("zip file does not exist, cannot set comment for zip file");
		}
		
		readZipInfo();
		
		if (this.zipModel == null) {
			throw new ZipException("zipModel is null, cannot update zip file");
		}
		
		if (zipModel.getEndCentralDirRecord() == null) {
			throw new ZipException("end of central directory is null, cannot set comment");
		}
		
		ArchiveMaintainer archiveMaintainer = new ArchiveMaintainer();
		archiveMaintainer.setComment(zipModel, comment);
	}
	
	/**
	 * Returns the comment set for the Zip file
	 * @return String
	 * @throws ZipException
	 */
	public String getComment() throws ZipException {
		return getComment(null);
	}
	
	/**
	 * Returns the comment set for the Zip file in the input encoding
	 * @param encoding
	 * @return String
	 * @throws ZipException
	 */
	public String getComment(String encoding) throws ZipException {
		if (encoding == null) {
			if (Zip4jUtil.isSupportedCharset(InternalZipConstants.CHARSET_COMMENTS_DEFAULT)) {
				encoding = InternalZipConstants.CHARSET_COMMENTS_DEFAULT;
			} else {
				encoding = InternalZipConstants.CHARSET_DEFAULT;
			}
		}
		
		if (Zip4jUtil.checkFileExists(file)) {
			checkZipModel();
		} else {
			throw new ZipException("zip file does not exist, cannot read comment");
		}
		
		if (this.zipModel == null) {
			throw new ZipException("zip model is null, cannot read comment");
		}
		
		if (this.zipModel.getEndCentralDirRecord() == null) {
			throw new ZipException("end of central directory record is null, cannot read comment");
		}
		
		if (this.zipModel.getEndCentralDirRecord().getCommentBytes() == null || 
				this.zipModel.getEndCentralDirRecord().getCommentBytes().length <= 0) {
			return null;
		}
		
		try {
			return new String(this.zipModel.getEndCentralDirRecord().getCommentBytes(), encoding);
		} catch (UnsupportedEncodingException e) {
			throw new ZipException(e);
		}
	}
	
	/**
	 * Loads the zip model if zip model is null and if zip file exists.
	 * @throws ZipException
	 */
	private void checkZipModel() throws ZipException {
		if (this.zipModel == null) {
			if (Zip4jUtil.checkFileExists(file)) {
				readZipInfo();
			} else {
				createNewZipModel();
			}
		}
	}
	
	/**
	 * Creates a new instance of zip model
	 * @throws ZipException
	 */
	private void createNewZipModel() {
		zipModel = new ZipModel();
		zipModel.setZipFile(file);
		zipModel.setFileNameCharset(fileNameCharset);
	}
	
	/**
	 * Zip4j will encode all the file names with the input charset. This method throws
	 * an exception if the Charset is not supported
	 * @param charsetName
	 * @throws ZipException
	 */
	public void setFileNameCharset(String charsetName) throws ZipException {
		if (!Zip4jUtil.isStringNotNullAndNotEmpty(charsetName)) {
			throw new ZipException("null or empty charset name");
		}
		
		if (!Zip4jUtil.isSupportedCharset(charsetName)) {
			throw new ZipException("unsupported charset: " + charsetName);
		}
		
		this.fileNameCharset = charsetName;
	}
	
	/**
	 * Returns an input stream for reading the contents of the Zip file corresponding
	 * to the input FileHeader. Throws an exception if the FileHeader does not exist
	 * in the ZipFile
	 * @param fileHeader
	 * @return ZipInputStream
	 * @throws ZipException
	 */
	public ZipInputStream getInputStream(FileHeader fileHeader) throws ZipException {
		if (fileHeader == null) {
			throw new ZipException("FileHeader is null, cannot get InputStream");
		}
		
		checkZipModel();
		
		if (zipModel == null) {
			throw new ZipException("zip model is null, cannot get inputstream");
		}
		
		Unzip unzip = new Unzip(zipModel);
		return unzip.getInputStream(fileHeader);
	}
	
	/**
	 * Checks to see if the input zip file is a valid zip file. This method
	 * will try to read zip headers. If headers are read successfully, this
	 * method returns true else false 
	 * @return boolean
	 * @since 1.2.3
	 */
	public boolean isValidZipFile() {
		try {
			readZipInfo();
			return true;
		} catch (Exception e) {
			return false;
		}
	}
	
	/**
	 * Returns the full file path+names of all split zip files 
	 * in an ArrayList. For example: If a split zip file(abc.zip) has a 10 split parts
	 * this method returns an array list with path + "abc.z01", path + "abc.z02", etc.
	 * Returns null if the zip file does not exist
	 * @return ArrayList of Strings
	 * @throws ZipException
	 */
	public ArrayList getSplitZipFiles() throws ZipException {
		checkZipModel();
		return Zip4jUtil.getSplitZipFiles(zipModel);
	}
	
	public ProgressMonitor getProgressMonitor() {
		return progressMonitor;
	}

	public boolean isRunInThread() {
		return runInThread;
	}

	public void setRunInThread(boolean runInThread) {
		this.runInThread = runInThread;
	}
	
	/**
	 * Returns the File object of the zip file 
	 * @return File
	 */
	public File getFile() {
		return new File(this.file);
	}
}
