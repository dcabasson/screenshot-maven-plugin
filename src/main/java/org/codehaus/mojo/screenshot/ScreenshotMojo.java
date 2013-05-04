package org.codehaus.mojo.screenshot;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.http.client.utils.URIUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.DirectoryWalkListener;
import org.codehaus.plexus.util.DirectoryWalker;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.ie.InternetExplorerDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.DriverCommand;

/**
 * Capture screenshots of web pages and store the corresponding screenshot in a
 * given folder.
 * 
 * @author Denis Cabasson
 * 
 * @goal screenshot
 * 
 * @phase generate-resources
 */
public class ScreenshotMojo extends AbstractMojo {
	
	/**
	 * Location of the screenshots.
	 * 
	 * @parameter expression="${project.build.directory}/screenshots/"
	 * @required
	 */
	private File outputDirectory;

	/**
	 * The URL used to access the wireframes in a browser.
	 * 
	 * @parameter default-value="http://cmp/prototype/role/"
	 * @required
	 */
	private URI rootURI;

	/**
	 * The folder holding all the wireframes.
	 * 
	 * @parameter
	 * @required
	 */
	private File inDirectory;
	
	/**
	 * List of selenium drivers to use in the test
	 * 
	 * @parameter
	 * @required
	 */
//	private String[] drivers;
	
    /**
     * A map of all the web drivers that will be used to capture screenshot versions of the wireframes.
     */
    protected Map<String, WebDriver> webDriverMap;

	public void execute() throws MojoExecutionException {
		webDriverMap=new HashMap<String, WebDriver>();
		final DesiredCapabilities firefoxCapabilities = DesiredCapabilities.firefox();
        firefoxCapabilities.setJavascriptEnabled(true);
        WebDriver firefoxDriver = new FirefoxDriver(firefoxCapabilities);
        firefoxDriver.manage().window().maximize();
        webDriverMap.put("firefox", firefoxDriver);
        final DesiredCapabilities ieCapabilities = DesiredCapabilities.internetExplorer();
        ieCapabilities.setJavascriptEnabled(true);
        WebDriver ieDriver = new InternetExplorerDriver(ieCapabilities);
        ieDriver.manage().window().maximize();
        webDriverMap.put("ie", ieDriver);
        final ChromeOptions chromeOptions = new ChromeOptions();
        WebDriver chromeDriver = new ChromeDriver(chromeOptions);
        chromeDriver.manage().window().maximize();
        webDriverMap.put("chrome",  chromeDriver);
        WebDriver smallChromeDriver = new ChromeDriver(chromeOptions);
        smallChromeDriver.manage().window().setSize(new Dimension(400, 800));;
        webDriverMap.put("chrome-small",  smallChromeDriver);
        if (this.inDirectory.exists() && this.inDirectory.isDirectory()) {
            final List<String> includes = new ArrayList<String>();
            includes.add("**/*.html");

            final List<String> excludes = new ArrayList<String>();
            excludes.add("all/archive/**");

            final DirectoryWalker dw = new DirectoryWalker();
            dw.setBaseDir(this.inDirectory);
            dw.setIncludes(includes);
            dw.setExcludes(excludes);

            dw.addDirectoryWalkListener(new ScreenshotDirectoryWalker());
            dw.scan();
        } else {
            getLog().debug("No wireframes in that folder");
        }
	}

	private class ScreenshotDirectoryWalker implements DirectoryWalkListener {

		private File baseDir;

		@Override
		public void directoryWalkStarting(File basedir) {
			this.baseDir = basedir;
		}

		@Override
		public void directoryWalkStep(int percentage, File file) {
			this.debug("Found file : " + file.getAbsolutePath());
			try {
				final String relativeFolder = getRelativePath(this.baseDir,
						file.getParentFile());
				this.debug("Relative folder : " + relativeFolder);
				File outputFolder = new File(outputDirectory, relativeFolder);
				outputFolder.mkdirs();
				this.debug("Output folder : " + outputFolder.getAbsolutePath());
				final String basename = FileUtils.basename(
						file.getAbsolutePath(), ".html");
				this.debug("basename : " + basename);
				URI pageURI = URIUtils.resolve(rootURI,
						StringUtils.join(relativeFolder.split("\\\\"), "/")
								+ "/" + basename + ".html");
				doAllScreenshots(pageURI, outputFolder.getAbsolutePath()
						+ File.separator + basename);
			} catch (IOException e) {
			}
		}

		@Override
		public void directoryWalkFinished() {
			this.debug("ALL DONE!");
		}

		@Override
		public void debug(String message) {
			getLog().debug(message);
		}
	}

	protected void doAllScreenshots(URI uri, String outBaseName)
			throws MalformedURLException {
		for (Map.Entry<String, WebDriver> entry : webDriverMap.entrySet()) {
			doScreenshot(entry.getValue(), entry.getKey(), uri, outBaseName);
		}
	}

	protected void doScreenshot(WebDriver driver, String qualifier, URI uri,
			String outBaseName) throws MalformedURLException {
		if (!(driver instanceof TakesScreenshot)) { // no point in going further
			return;
		}
		driver.navigate().to(uri.toURL());
		try {
			TimeUnit.SECONDS.sleep(1);
		} catch (InterruptedException e1) {
		}

		File tempScreenshot = ((TakesScreenshot) driver)
				.getScreenshotAs(OutputType.FILE);

		try {
			FileUtils.copyFile(tempScreenshot, new File(outBaseName + "-"
					+ qualifier + ".jpg"));
		} catch (IOException e) {
		}
	}

	/**
	 * Computes the path for a file relative to a given base, or fails if the
	 * only shared directory is the root and the absolute form is better.
	 * 
	 * @param base
	 *            File that is the base for the result
	 * @param name
	 *            File to be "relativized"
	 * @return the relative name
	 * @throws IOException
	 *             if files have no common sub-directories, i.e. at best share
	 *             the root prefix "/" or "C:\"
	 */

	public static String getRelativePath(File base, File name)
			throws IOException {
		File parent = base.getParentFile();

		if (parent == null) {
			throw new IOException("No common directory");
		}

		String bpath = base.getCanonicalPath();
		String fpath = name.getCanonicalPath();

		if (fpath.startsWith(bpath)) {
			return fpath.substring(bpath.length() + 1);
		} else {
			return (".." + File.separator + getRelativePath(parent, name));
		}
	}
}
