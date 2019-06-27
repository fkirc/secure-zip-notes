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

package com.ditronic.securezipnotes.zip4j.unzip;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;

import com.ditronic.securezipnotes.zip4j.exception.ZipException;
import com.ditronic.securezipnotes.zip4j.io.ZipInputStream;
import com.ditronic.securezipnotes.zip4j.model.CentralDirectory;
import com.ditronic.securezipnotes.zip4j.model.FileHeader;
import com.ditronic.securezipnotes.zip4j.model.UnzipParameters;
import com.ditronic.securezipnotes.zip4j.model.ZipModel;
import com.ditronic.securezipnotes.zip4j.progress.ProgressMonitor;
import com.ditronic.securezipnotes.zip4j.util.InternalZipConstants;
import com.ditronic.securezipnotes.zip4j.util.Zip4jUtil;

public class Unzip {
	
	private ZipModel zipModel;
	
	public Unzip(ZipModel zipModel) throws ZipException {
		
		if (zipModel == null) {
			throw new ZipException("ZipModel is null");
		}
		
		this.zipModel = zipModel;
	}
	
	public void extractAll(final UnzipParameters unzipParameters, final String outPath,
			final ProgressMonitor progressMonitor, boolean runInThread) throws ZipException {
		
		CentralDirectory centralDirectory = zipModel.getCentralDirectory();
		
		if (centralDirectory == null || 
				centralDirectory.getFileHeaders() == null) {
			throw new ZipException("invalid central directory in zipModel");
		}
		
		final ArrayList fileHeaders = centralDirectory.getFileHeaders();
		
		progressMonitor.setCurrentOperation(ProgressMonitor.OPERATION_EXTRACT);
		progressMonitor.setTotalWork(calculateTotalWork(fileHeaders));
		progressMonitor.setState(ProgressMonitor.STATE_BUSY);
		
		if (runInThread) {
			Thread thread = new Thread(InternalZipConstants.THREAD_NAME) {
				public void run() {
					try {
						initExtractAll(fileHeaders, unzipParameters, progressMonitor, outPath);
						progressMonitor.endProgressMonitorSuccess();
					} catch (ZipException e) {
					}
				}
			};
			thread.start();
		} else {
			initExtractAll(fileHeaders, unzipParameters, progressMonitor, outPath);
		}
		
	}
	
	private void initExtractAll(ArrayList fileHeaders, UnzipParameters unzipParameters, 
			ProgressMonitor progressMonitor, String outPath) throws ZipException {
		
		for (int i = 0; i < fileHeaders.size(); i++) {
			FileHeader fileHeader = (FileHeader)fileHeaders.get(i);
			//initExtractFile(fileHeader, unzipParameters, progressMonitor);
			if (progressMonitor.isCancelAllTasks()) {
				progressMonitor.setResult(ProgressMonitor.RESULT_CANCELLED);
				progressMonitor.setState(ProgressMonitor.STATE_READY);
				return;
			}
		}
	}
	
	public ByteArrayOutputStream extractFile(final FileHeader fileHeader,
											 final UnzipParameters unzipParameters,
											 final ProgressMonitor progressMonitor, boolean runInThread) throws ZipException {
		if (fileHeader == null) {
			throw new ZipException("fileHeader is null");
		}
		
		progressMonitor.setCurrentOperation(ProgressMonitor.OPERATION_EXTRACT);
		progressMonitor.setTotalWork(fileHeader.getCompressedSize());
		progressMonitor.setState(ProgressMonitor.STATE_BUSY);
		progressMonitor.setPercentDone(0);
		progressMonitor.setFileName(fileHeader.getFileName());
		
		if (runInThread) {
			throw new RuntimeException("Unsupported");
			/*Thread thread = new Thread(InternalZipConstants.THREAD_NAME) {
				public void run() {
					try {
						initExtractFile(fileHeader, unzipParameters, progressMonitor);
						progressMonitor.endProgressMonitorSuccess();
					} catch (ZipException e) {
					}
				}
			};
			thread.start();*/
		} else {
			ByteArrayOutputStream os = initExtractFile(fileHeader, unzipParameters, progressMonitor);
			progressMonitor.endProgressMonitorSuccess();
			return os;
		}
	}
	
	private ByteArrayOutputStream initExtractFile(FileHeader fileHeader,
			UnzipParameters unzipParameters, ProgressMonitor progressMonitor) throws ZipException {

		if (fileHeader == null) {
			throw new ZipException("fileHeader is null");
		}
		
		try {
			progressMonitor.setFileName(fileHeader.getFileName());
			
			// If file header is a directory, then check if the directory exists
			// If not then create a directory and return
			if (fileHeader.isDirectory()) {
				throw new RuntimeException("Unsupported");
				/*try {
					String fileName = fileHeader.getFileName();
					if (!Zip4jUtil.isStringNotNullAndNotEmpty(fileName)) {
						return null;
					}
				} catch (Exception e) {
					progressMonitor.endProgressMonitorError(e);
					throw new ZipException(e);
				}*/
			} else {
				//Create Directories
				//checkOutputDirectoryStructure(fileHeader, outPath, newFileName);
				
				UnzipEngine unzipEngine = new UnzipEngine(zipModel, fileHeader);
				try {
					return unzipEngine.unzipFile(progressMonitor, unzipParameters);
				} catch (Exception e) {
					progressMonitor.endProgressMonitorError(e);
					throw new ZipException(e);
				}
			}
		} catch (ZipException e) {
			progressMonitor.endProgressMonitorError(e);
			throw e;
		} catch (Exception e) {
			progressMonitor.endProgressMonitorError(e);
			throw new ZipException(e);
		}
	}
	
	public ZipInputStream getInputStream(FileHeader fileHeader) throws ZipException {
		UnzipEngine unzipEngine = new UnzipEngine(zipModel, fileHeader);
		return unzipEngine.getInputStream();
	}
	
	private void checkOutputDirectoryStructure(FileHeader fileHeader, String outPath, String newFileName) throws ZipException {
		if (fileHeader == null || !Zip4jUtil.isStringNotNullAndNotEmpty(outPath)) {
			throw new ZipException("Cannot check output directory structure...one of the parameters was null");
		}
		
		String fileName = fileHeader.getFileName();
		
		if (Zip4jUtil.isStringNotNullAndNotEmpty(newFileName)) {
			fileName = newFileName;
		}
		
		if (!Zip4jUtil.isStringNotNullAndNotEmpty(fileName)) {
			// Do nothing
			return;
		}
		
		String compOutPath = outPath + fileName;
		try {
			File file = new File(compOutPath);
			String parentDir = file.getParent();
			File parentDirFile = new File(parentDir);
			if (!parentDirFile.exists()) {
				parentDirFile.mkdirs();
			}
		} catch (Exception e) {
			throw new ZipException(e);
		}
	}
	
	private long calculateTotalWork(ArrayList fileHeaders) throws ZipException {
		
		if (fileHeaders == null) {
			throw new ZipException("fileHeaders is null, cannot calculate total work");
		}
		
		long totalWork = 0;
		
		for (int i = 0; i < fileHeaders.size(); i++) {
			FileHeader fileHeader = (FileHeader)fileHeaders.get(i);
			if (fileHeader.getZip64ExtendedInfo() != null && 
					fileHeader.getZip64ExtendedInfo().getUnCompressedSize() > 0) {
				totalWork += fileHeader.getZip64ExtendedInfo().getCompressedSize();
			} else {
				totalWork += fileHeader.getCompressedSize();
			}
			
		}
		
		return totalWork;
	}
	
}
