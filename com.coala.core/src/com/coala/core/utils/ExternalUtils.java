package com.coala.core.utils;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

public class ExternalUtils {

  /**
   * Invoke coala-json.
   * 
   * @param file
   *          The IFile to run the analysis on.
   * @param bear
   *          The coala Bear to use for analysis.
   * @throws IOException
   *           exception
   * @throws ExecuteException
   *           exception
   * @throws InterruptedException
   *           exception
   */
  public static void runBearOnFile(final IFile file, String bear)
      throws ExecuteException, IOException, InterruptedException {
    String path = file.getRawLocation().toOSString();
    CommandLine cmdLine = new CommandLine("coala-json");
    cmdLine.addArgument("-f" + path);
    cmdLine.addArgument("-b" + bear);
    System.out.println(cmdLine.toString());

    final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    PumpStreamHandler pumpStreamHandler = new PumpStreamHandler(stdout);

    // Asynchronously handle coala-json output
    DefaultExecuteResultHandler resultHandler = new DefaultExecuteResultHandler() {

      @Override
      public void onProcessComplete(int exitValue) {
        try {
          processJsonAndMark(stdout.toString(), file);
        } catch (IOException ex) {
          ex.printStackTrace();
        }
      }

      @Override
      public void onProcessFailed(ExecuteException executeException) {
        System.out.println("Running coala failed");
        executeException.printStackTrace();
      }
    };

    // Timeout command execution after 60 seconds.
    ExecuteWatchdog watchdog = new ExecuteWatchdog(60 * 1000);

    Executor executor = new DefaultExecutor();
    executor.setWatchdog(watchdog);
    executor.setExitValue(1);
    executor.setStreamHandler(pumpStreamHandler);
    executor.execute(cmdLine, resultHandler);
  }

  /**
   * Run coala using the .coafile in the project directory.
   * 
   * @param path
   *          The path to the project's root.
   * @param project
   *          The IProject object of the current project.
   */
  public static void runcoafile(final String path, final IProject project)
      throws ExecuteException, IOException {
    File cwd = new File(path);
    CommandLine cmdLine = new CommandLine("coala-json");
    final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    PumpStreamHandler pumpStreamHandler = new PumpStreamHandler(stdout);

    // Asynchronously handle coala-json output
    DefaultExecuteResultHandler resultHandler = new DefaultExecuteResultHandler() {

      @Override
      public void onProcessComplete(int exitValue) {
        try {
          processJsonAndMark(stdout.toString(), project);
        } catch (IOException ex) {
          ex.printStackTrace();
        }
      }

      @Override
      public void onProcessFailed(ExecuteException executeException) {
        System.out.println("Running coala failed with output: " + stdout.toString());
        executeException.printStackTrace();
      }
    };

    // Timeout command execution after 60 seconds.
    ExecuteWatchdog watchdog = new ExecuteWatchdog(60 * 1000);

    Executor executor = new DefaultExecutor();
    executor.setWatchdog(watchdog);
    executor.setExitValue(1);
    executor.setStreamHandler(pumpStreamHandler);
    executor.setWorkingDirectory(cwd);
    executor.execute(cmdLine, resultHandler);
  }

  /**
   * Process the JSON output of coala and add marker for each problem.
   * 
   * @param json
   *          Output of running coala-json.
   * @param file
   *          The IFile to add markers on.
   * @throws IOException
   *           exception
   */
  public static void processJsonAndMark(String json, IFile file) throws IOException {
    JSONObject jsonObject = new JSONObject(json);
    JSONArray result = jsonObject.getJSONObject("results").getJSONArray("default");
    for (int i = 0; i < result.length(); i++) {
      String message = result.getJSONObject(i).getString("message");
      String origin = result.getJSONObject(i).getString("origin");
      int severity = result.getJSONObject(i).getInt("severity");
      JSONArray affectedCodeArray = result.getJSONObject(i).getJSONArray("affected_code");
      for (int j = 0; j < affectedCodeArray.length(); j++) {
        int endLine = affectedCodeArray.getJSONObject(j).getJSONObject("end").getInt("line");
        createCoolMarker(file, endLine, 3 - severity, message);
      }
    }
  }

  /**
   * Process the JSON output of coala and add marker for each problem.
   * 
   * @param json
   *          Output of running coala-json.
   * @param project
   *          The IProject containing files to add markers on.
   * @throws IOException
   *           exception
   */
  public static void processJsonAndMark(String json, IProject project) throws IOException {
    JSONObject jsonObject = new JSONObject(json);
    JSONArray result = jsonObject.getJSONObject("results").getJSONArray("default");
    for (int i = 0; i < result.length(); i++) {
      String projectPath = project.getLocation().toOSString();
      String filePath = result.getJSONObject(i).getJSONArray("affected_code").getJSONObject(0)
          .getString("file");
      IPath path = new Path(filePath.substring(projectPath.length()));
      IFile file = project.getFile(path);
      String message = result.getJSONObject(i).getString("message");
      String origin = result.getJSONObject(i).getString("origin");
      int severity = result.getJSONObject(i).getInt("severity");
      JSONArray affectedCodeArray = result.getJSONObject(i).getJSONArray("affected_code");
      for (int j = 0; j < affectedCodeArray.length(); j++) {
        int endLine = affectedCodeArray.getJSONObject(j).getJSONObject("end").getInt("line");
        createCoolMarker(file, endLine, 3 - severity, message);
      }
    }
  }

  /**
   * Creates a problem marker.
   * 
   * @param file
   *          The IFile to add markers on.
   * @param lineNum
   *          Line number of marker.
   * @param flag
   *          Severity 1 for error, 2 for warning.
   * @param message
   *          Problem message on marker.
   */
  public static void createCoolMarker(IFile file, int lineNum, int flag, String message) {
    IResource resource = (IResource) file;
    try {
      IMarker marker = resource.createMarker("com.coala.core.coolproblem");
      marker.setAttribute(IMarker.LINE_NUMBER, lineNum);
      if (flag == 1) {
        marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
      } else if (flag == 2) {
        marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_WARNING);
      }
      marker.setAttribute(IMarker.MESSAGE, message);
    } catch (CoreException ex) {
      ex.printStackTrace();
    }
  }
}
