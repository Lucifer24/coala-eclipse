package com.coala.core.handlers;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.ui.PlatformUI;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The PluginHandler extends AbstractHandler, an IHandler base class.
 * 
 * @see org.eclipse.core.commands.IHandler
 * @see org.eclipse.core.commands.AbstractHandler
 */
public class Plugin extends AbstractHandler {

	/**
	 * The constructor.
	 * 
	 * @throws IOException
	 */
	public Plugin() {
	}

	public Object execute(ExecutionEvent event) throws ExecutionException {
		IFile file = (IFile) PlatformUI.getWorkbench()
									   .getActiveWorkbenchWindow()
									   .getActivePage()
									   .getActivePart()
									   .getSite()
									   .getPage()
									   .getActiveEditor()
									   .getEditorInput()
									   .getAdapter(IFile.class);
		new RemoveMarkers().execute(event);
		runcoalaOnFile(file, "CheckstyleBear");
		return null;
	}
	
	/**
	 * Invoke coala-json.
	 * @param file The IFile to run the analysis on.
	 * @param bear The coala Bear to use for analysis.
	 */
	public void runcoalaOnFile(IFile file, String bear) {
		String path = file.getRawLocation().toOSString();
		String cmd = "coala-json -f " + path + " -b " + bear;
		System.out.println(cmd);
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				Process proc = null;
				try {
					proc = Runtime.getRuntime().exec(cmd);
				} catch (IOException e) {
					System.out.println("Running coala failed.");
					e.printStackTrace();
				}
				InputStream is = proc.getInputStream();
				Scanner s = new Scanner(is);
				s.useDelimiter("\\A");
				String val = "";
				if (s.hasNext()) {
					val = s.next();
				} else {
					val = "";
				}
				s.close();
				try {
					processJSONAndMark(val, file);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}).start();
	}

	/**
	 * Process the JSON output of coala and add marker for each problem.
	 * @param json Output of running coala-json.
	 * @param file The IFile to add markers on.
	 * @throws IOException
	 */
	public void processJSONAndMark(String json, IFile file) throws IOException {
		JSONObject jsonObject = new JSONObject(json);
		JSONArray result = jsonObject.getJSONObject("results").getJSONArray("default");
		for (int i = 0; i < result.length(); i++) {
			String message = result.getJSONObject(i).getString("message");
			String origin = result.getJSONObject(i).getString("origin");
			int severity = result.getJSONObject(i).getInt("severity");
			JSONArray affectedCodeArray = result.getJSONObject(i).getJSONArray("affected_code");
			for (int j = 0; j < affectedCodeArray.length(); j++) {
				int end_line = affectedCodeArray.getJSONObject(j).getJSONObject("end").getInt("line");
				createCoolMarker(file, end_line, 3 - severity, message);
			}
		}
	}

	/**
	 * Creates a problem marker.
	 * @param file     The IFile to add markers on.
	 * @param line_num Line number of marker.
	 * @param flag	   Severity 1 for error, 2 for warning.
	 * @param message  Problem message on marker.
	 */
	public void createCoolMarker(IFile file, int line_num, int flag, String message) {
		IResource resource = (IResource) file;
		try {
			IMarker marker = resource.createMarker("com.coala.core.coolproblem");
			marker.setAttribute(IMarker.LINE_NUMBER, line_num);
			if (flag == 1) {
				marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
			} else if (flag == 2) {
				marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_WARNING);
			}
			marker.setAttribute(IMarker.MESSAGE, message);
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}

}