package flexagon.fd.plugin.jenkins.operations;

import static com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials;

import java.io.IOException;
import java.io.PrintStream;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import flexagon.fd.plugin.jenkins.utils.BuildFileInput;
import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.google.common.base.Strings;

import flexagon.fd.plugin.jenkins.utils.Credential;
import flexagon.fd.plugin.jenkins.utils.KeyValuePair;
import flexagon.fd.plugin.jenkins.utils.PluginConstants;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.ItemGroup;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * @author Ben Hoffman &amp; Victor Krieg
 */
public final class TriggerFlexDeployProject extends Notifier {
	private final String mUrl;
	private final String mProjectStreamName;
	private final String mPackageName;
	private final String mReleaseName;
	private final String mProjectPath;
	private final String mEnvironmentCode;
	private final String mWorkflowVersionOverride;
	private final Boolean mForce;
	private final Boolean mWait;
	private final String issueNumbers;

	private List<BuildFileInput> buildFileInputs;
	private List<KeyValuePair> inputs;
	private List<KeyValuePair> flexFields;
	private final Credential credential;
	private static PrintStream LOG;

	private EnvVars envVars = new EnvVars();
	private UsernamePasswordCredentials creds;

	@DataBoundConstructor
	public TriggerFlexDeployProject(String mUrl, String mProjectStreamName, String mPackageName, String mReleaseName,
			String mProjectPath, String mEnvironmentCode, String mWorkflowVersionOverride, Boolean mForce,
			Boolean mWait, String issueNumbers, List<BuildFileInput> buildFileInputs, List<KeyValuePair> inputs,
			List<KeyValuePair> flexFields, Credential credential) {
		this.mUrl = mUrl;
		this.mProjectStreamName = mProjectStreamName;
		this.mPackageName = mPackageName;
		this.mReleaseName = mReleaseName;
		this.mProjectPath = mProjectPath;
		this.mEnvironmentCode = mEnvironmentCode.toUpperCase();
		this.mWorkflowVersionOverride = mWorkflowVersionOverride;
		this.mForce = mForce;
		this.mWait = mWait;
		this.issueNumbers = issueNumbers;
		this.buildFileInputs = buildFileInputs;
		this.inputs = inputs;
		this.flexFields = flexFields;
		this.credential = credential;
	}

	public String getmUrl() {
		return mUrl;
	}

	public String getmProjectStreamName() {
		return mProjectStreamName;
	}

	public String getmPackageName() {
		return mPackageName;
	}

	public String getmReleaseName() {
		return mReleaseName;
	}

	public String getmProjectPath() {
		return mProjectPath;
	}

	public String getmEnvironmentCode() {
		return mEnvironmentCode;
	}

	public String getmWorkflowVersionOverride() {
		return mWorkflowVersionOverride;
	}

	public Boolean getmForce() {
		return mForce;
	}

	public Boolean getmWait() {
		return mWait;
	}

	public String getIssueNumbers() {
		return issueNumbers;
	}

	public List<BuildFileInput> getBuildFileInputs() {
		return buildFileInputs;
	}

	public void setBuildFileInputs(List<BuildFileInput> buildFileInputs) {
		this.buildFileInputs = buildFileInputs;
	}

	public List<KeyValuePair> getInputs() {
		return inputs;
	}

	public void setInputs(List<KeyValuePair> inputs) {
		this.inputs = inputs;
	}

	public List<KeyValuePair> getFlexFields() {
		return flexFields;
	}

	public void setFlexFields(List<KeyValuePair> flexFields) {
		this.flexFields = flexFields;
	}

	public Credential getCredential() {
		return credential;
	}

	private String getInputOrVar(String pInputValue) {
		if (envVars.containsKey(pInputValue)) {
			return envVars.get(pInputValue);
		} else {
			return pInputValue;
		}

	}

	private static void setLogger(BuildListener listener) {
		LOG = listener.getLogger();
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
		setLogger(listener);
		String workflowStatus;

		LOG.println("Building authentication object...");
		buildAuth(credential);

		try {
			LOG.println("Getting env vars...");
			envVars = build.getEnvironment(listener);

			LOG.println("Executing REST request to FlexDeploy.");
			workflowStatus = executeRequest();

			if (workflowStatus.equals(PluginConstants.WORKFLOW_STATUS_COMPLETED)) {
				LOG.println("Execution completed successfully.");
				return true;
			} else {
				LOG.println("Execution " + workflowStatus.toLowerCase());
				return false;
			}

		} catch (Exception e) {
			LOG.println("Unknown error has occurred: " + e);
			return false;
		}

	}

	private List<KeyValuePair> resolveInputs() // populates the Input list exactly the same way as FlexFields
	{
		List<KeyValuePair> kvp = new ArrayList<>();

		for (KeyValuePair pair : inputs) {
			String key = pair.getKey();
			String value = pair.getValue();

			kvp.add(new KeyValuePair(key, value));
		}

		LOG.println("Added " + kvp.size() + " Inputs to body.");

		return kvp;
	}

	private List<KeyValuePair> resolveFlexFields() // populates the FlexField list exactly the same way as inputs
	{
		List<KeyValuePair> kvp = new ArrayList<>();

		for (KeyValuePair pair : flexFields) {
			String key = pair.getKey();
			String value = pair.getValue();

			kvp.add(new KeyValuePair(key, value));
		}

		LOG.println("Added " + kvp.size() + " Flex Fields to body.");

		return kvp;
	}

	private List<BuildFileInput> resolveBuildFileInputs() // populates the FlexField list exactly the same way as inputs
	{
		List<BuildFileInput> BFIs = new ArrayList<>();

		for (BuildFileInput bfi : buildFileInputs) {
			Long projectObjectId = bfi.getProjectObjectId();
			String scmRevision = bfi.getScmRevision();
			Long fromPackageObjectId = bfi.getProjectObjectId();

			BFIs.add(new BuildFileInput(projectObjectId, scmRevision, fromPackageObjectId));
		}

		LOG.println("Added " + BFIs.size() + " Build File Inputs to body.");

		return BFIs;
	}

	private static String removeEndSlash(String pUrl) {
		String s = pUrl;

		while (s.endsWith("/") || s.endsWith("\\")) {
			s = s.substring(0, s.length() - 1);
		}

		return s;
	}

	private String waitForFlexDeploy(final String pWorkflowId) {
		ExecutorService executor = Executors.newSingleThreadExecutor();
		final Future<String> future = executor.submit(new Callable<String>() {
			@Override
			public String call() throws Exception {
				String status = getWorkflowExecutionStatus(pWorkflowId);

				while (!status.equals(PluginConstants.WORKFLOW_STATUS_ABORTED)
						&& !status.equals(PluginConstants.WORKFLOW_STATUS_COMPLETED)
						&& !status.equals(PluginConstants.WORKFLOW_STATUS_REJECTED)
						&& !status.equals(PluginConstants.WORKFLOW_STATUS_FAILED)) {
					LOG.println("Workflow execution status is " + status + ". Checking again in 5 seconds...");
					Thread.sleep(PluginConstants.TIMEOUT_CONNECTION_VALIDATION);
					status = getWorkflowExecutionStatus(pWorkflowId);
				}

				return status;
			}
		});

		try {
			return future.get(PluginConstants.TIMEOUT_WORKFLOW_EXECUTION, TimeUnit.MINUTES);

		} catch (TimeoutException | InterruptedException | ExecutionException e) {
			future.cancel(true);
			LOG.println("Workflow execution is taking too long, stopping plugin execution with success.");
			LOG.println(e);
			return PluginConstants.WORKFLOW_STATUS_COMPLETED;
		} finally {
			executor.shutdownNow();
		}

	}

	private String executeRequest() throws Exception {
		LOG.println("Building JSON Body...");
		JSONObject body = buildJSONBody();

		LOG.println("Building URL...");

		String url = removeEndSlash(mUrl);
		url = url + PluginConstants.URL_SUFFIX_BUILD_PROJECT;

		CloseableHttpClient httpClient = HttpClients.createDefault();
		HttpPost request = new HttpPost(url);

		LOG.println("Setting Body");
		request.setEntity(new StringEntity(body.toString()));
		request.setHeader("Content-type", PluginConstants.CONTENT_TYPE_APP_JSON);

		int returnCode = 0;
		int workflowId = -1;

		try {
			LOG.println("Executing Request");
			request.addHeader(new BasicScheme().authenticate(creds, request, null));
			CloseableHttpResponse response = httpClient.execute(request);
			String result = EntityUtils.toString(response.getEntity());
			returnCode = response.getStatusLine().getStatusCode();

			LOG.println("Return status: " + response.getStatusLine().toString());

			if (returnCode == 201) {
				workflowId = parseJson(result, PluginConstants.JSON_WORKFLOW_REQUEST_ID);
				LOG.println("Successfully got workflow Id: " + workflowId);
				if (mWait) {
					return waitForFlexDeploy(Integer.toString(workflowId));
				} else {
					return PluginConstants.WORKFLOW_STATUS_COMPLETED;
				}

			} else {
				throw new AbortException("Request failed. Could not execute workflow.");
			}

		} catch (AbortException a) {
			throw a;
		} catch (IOException e) {
			LOG.println("Unknown error occurred in the FlexDeploy REST call. " + e);
			return PluginConstants.WORKFLOW_STATUS_FAILED;
		} catch (AuthenticationException ae) {
			LOG.println(ae);
			return PluginConstants.ERROR_LOGIN_FAILURE;
		} finally {
			httpClient.close();
		}

	}

	private String getWorkflowExecutionStatus(String pWorkflowId) throws IOException {
		String url = removeEndSlash(mUrl);
		url = url + PluginConstants.URL_SUFFIX_GET_WORKFLOW_REQUEST + "/" + pWorkflowId;

		CloseableHttpClient httpClient = HttpClients.createDefault();
		HttpGet request = new HttpGet(url);

		int returnCode = 0;
		String workflowStatus = PluginConstants.WORKFLOW_STATUS_COMPLETED;

		try {
			request.addHeader(new BasicScheme().authenticate(creds, request, null));
			CloseableHttpResponse response = httpClient.execute(request);
			String result = EntityUtils.toString(response.getEntity());
			returnCode = response.getStatusLine().getStatusCode();

			if (returnCode == 200) {
				workflowStatus = new org.json.JSONObject(result).getString("requestStatus");
			} else {
				LOG.println("WARNING: Failed to check workflow status.");
			}
		} catch (Exception e) {
			LOG.println("WARNING: Failed to check workflow status. " + e);
		} finally {
			httpClient.close();
		}

		return workflowStatus;

	}

	private int parseSearchJson(String json, String key) {
		org.json.JSONObject jsonObject = new org.json.JSONObject(json);

		org.json.JSONArray jsonArray = jsonObject.getJSONArray("items");

		return jsonArray.getJSONObject(0).getInt(key);
	}

	private int parseReleaseJson(String json, int projectId) throws Exception {
		org.json.JSONArray releaseArray = new org.json.JSONArray(json);

		if (releaseArray.length() > 1) {
			throw new AbortException("Release name not specific enough. Found " + releaseArray.length()
					+ " releases. - " + mReleaseName);
		} else if (releaseArray.length() == 0) {
			throw new AbortException("Found no matching release from name: " + mReleaseName);
		}

		org.json.JSONObject release = releaseArray.getJSONObject(0);

		org.json.JSONArray projectArray = release.getJSONArray("projects");

		if (projectArray.length() == 0) {
			throw new AbortException("No project configurations for release: " + release.getString("releaseName"));
		}

		int releaseId = -1;

		for (int i = 0; i < projectArray.length(); i++) {
			org.json.JSONObject project = projectArray.getJSONObject(i);

			if (project.getInt(PluginConstants.JSON_PROJECT_ID) == projectId) {
				releaseId = release.getInt("releaseId");
			}
		}

		if (releaseId == -1) {
			throw new AbortException("Project not configured for release: " + release.getString("releaseName"));
		}

		return releaseId;
	}

	private int parseJson(String json, String key) {
		org.json.JSONObject jsonObject = new org.json.JSONObject(json);

		return jsonObject.getInt(key);
	}

	private int getProjectId() throws Exception {
		List<String> folders = Arrays.asList(mProjectPath.split("/"));

		if (folders.size() < 3) {
			throw new AbortException("Project Path invalid: " + mProjectPath);
		} else if (!folders.get(0).equals("FlexDeploy")) {
			throw new AbortException(
					"Project Path invalid: " + mProjectPath + " - Path must follow format FlexDeploy/path/to/project");
		}

		int folderId = -1;

		for (int i = 1; i < folders.size() - 1; i++) {
			if (i == 1) {
				folderId = getFolderId(folders.get(i), PluginConstants.FLEXDEPLOY_FOLDER_ID);
			} else {
				folderId = getFolderId(folders.get(i), folderId);
			}
		}

		if (folderId == -1) {
			throw new AbortException("Project Path invalid: " + mProjectPath);
		}

		String projectName = folders.get(folders.size() - 1);
		projectName = projectName.replace(" ", "%20");

		String url = removeEndSlash(mUrl);
		url = url + PluginConstants.URL_SUFFIX_GET_PROJECT + PluginConstants.URL_SUFFIX_SEARCH_PROJECT_1 + projectName
				+ PluginConstants.URL_SUFFIX_SEARCH_PROJECT_2 + folderId;

		CloseableHttpClient httpClient = HttpClients.createDefault();
		HttpGet request = new HttpGet(url);

		int returnCode = 0;
		int projectId = -1;

		try {
			request.addHeader(new BasicScheme().authenticate(creds, request, null));
			CloseableHttpResponse response = httpClient.execute(request);
			String result = EntityUtils.toString(response.getEntity());
			returnCode = response.getStatusLine().getStatusCode();

			if (returnCode == 200) {
				projectId = parseSearchJson(result, PluginConstants.JSON_PROJECT_ID);
			} else {
				throw new AbortException("Request failed. Could not get project id from " + mProjectPath);
			}
		} catch (Exception e) {
			LOG.println(e);
		} finally {
			httpClient.close();
		}

		return projectId;
	}

	private int getFolderId(String folderName, int parentFolderId) throws Exception {
		folderName = folderName.replace(" ", "%20");

		String url = removeEndSlash(mUrl);
		url = url + PluginConstants.URL_SUFFIX_SEARCH_FOLDER_1 + folderName + PluginConstants.URL_SUFFIX_SEARCH_FOLDER_2
				+ parentFolderId;

		CloseableHttpClient httpClient = HttpClients.createDefault();
		HttpGet request = new HttpGet(url);

		int returnCode = 0;
		int folderId = -1;

		try {
			request.addHeader(new BasicScheme().authenticate(creds, request, null));
			CloseableHttpResponse response = httpClient.execute(request);
			String result = EntityUtils.toString(response.getEntity());
			returnCode = response.getStatusLine().getStatusCode();

			if (returnCode == 200) {
				folderId = parseSearchJson(result, PluginConstants.JSON_FOLDER_ID);
			} else {
				throw new AbortException("Request failed. Could not get folder id from " + folderName);
			}
		} catch (Exception e) {
			LOG.println(e);
		} finally {
			httpClient.close();
		}

		return folderId;
	}

	private int getEnvironmentId() throws Exception {
		String url = removeEndSlash(mUrl);
		url = url + PluginConstants.URL_SUFFIX_SEARCH_ENVIRONMENT + mEnvironmentCode;

		CloseableHttpClient httpClient = HttpClients.createDefault();
		HttpGet request = new HttpGet(url);

		int returnCode = 0;
		int environmentId = -1;

		try {
			request.addHeader(new BasicScheme().authenticate(creds, request, null));
			CloseableHttpResponse response = httpClient.execute(request);
			String result = EntityUtils.toString(response.getEntity());
			returnCode = response.getStatusLine().getStatusCode();

			if (returnCode == 200) {
				environmentId = parseSearchJson(result, PluginConstants.JSON_ENVIRONMENT_ID);
			} else {
				throw new AbortException("Request failed. Could not get environment id from " + mEnvironmentCode);
			}
		} catch (Exception e) {
			LOG.println(e);
		} finally {
			httpClient.close();
		}

		return environmentId;
	}

	private int getProjectStreamId(int projectId) throws Exception {
		String url = removeEndSlash(mUrl);
		url = url + PluginConstants.URL_SUFFIX_GET_PROJECT + '/' + projectId + PluginConstants.URL_SUFFIX_SEARCH_BRANCH
				+ mProjectStreamName;

		CloseableHttpClient httpClient = HttpClients.createDefault();
		HttpGet request = new HttpGet(url);

		int returnCode = 0;
		int projectStreamId = -1;

		try {
			request.addHeader(new BasicScheme().authenticate(creds, request, null));
			CloseableHttpResponse response = httpClient.execute(request);
			String result = EntityUtils.toString(response.getEntity());
			returnCode = response.getStatusLine().getStatusCode();

			if (returnCode == 200) {
				projectStreamId = parseSearchJson(result, PluginConstants.JSON_PROJECT_BRANCH_ID);
			} else {
				throw new AbortException("Request failed. Could not get project branch id from " + mProjectStreamName);
			}
		} catch (Exception e) {
			LOG.println(e);
		} finally {
			httpClient.close();
		}

		return projectStreamId;
	}

	private int getReleaseId(int projectId) throws Exception {
		String relName = mReleaseName.replace(" ", "%20");

		String url = removeEndSlash(mUrl);
		url = url + PluginConstants.URL_SUFFIX_SEARCH_RELEASE + relName;

		CloseableHttpClient httpClient = HttpClients.createDefault();
		HttpGet request = new HttpGet(url);

		int returnCode = 0;
		int releaseId = -1;

		try {
			request.addHeader(new BasicScheme().authenticate(creds, request, null));
			CloseableHttpResponse response = httpClient.execute(request);
			String result = EntityUtils.toString(response.getEntity());
			returnCode = response.getStatusLine().getStatusCode();

			if (returnCode == 200) {
				releaseId = parseReleaseJson(result, projectId);
			} else {
				throw new AbortException("Request failed. Could not get release id from " + mProjectStreamName);
			}
		} catch (Exception e) {
			LOG.println(e);
		} finally {
			httpClient.close();
		}

		return releaseId;
	}

	private org.json.JSONObject getProjectById(int projectId) throws Exception {
		String url = removeEndSlash(mUrl);
		url = url + PluginConstants.URL_SUFFIX_GET_PROJECT + '/' + projectId;

		CloseableHttpClient httpClient = HttpClients.createDefault();
		HttpGet request = new HttpGet(url);

		int returnCode = 0;
		org.json.JSONObject projectObject = new org.json.JSONObject();

		try {
			request.addHeader(new BasicScheme().authenticate(creds, request, null));
			CloseableHttpResponse response = httpClient.execute(request);
			String result = EntityUtils.toString(response.getEntity());
			returnCode = response.getStatusLine().getStatusCode();

			if (returnCode == 200) {
				projectObject = new org.json.JSONObject(result);
			}
		} catch (Exception e) {
			LOG.println(e);
		} finally {
			httpClient.close();
		}

		return projectObject;
	}

	private int getWorkflowId(org.json.JSONObject projectObject) {
		org.json.JSONArray workflowsArray = projectObject.getJSONArray("projectWorkflows");

		int workflowId = -1;

		for (int i = 0; i < workflowsArray.length(); i++) {
			org.json.JSONObject workflowObject = workflowsArray.getJSONObject(i);

			if (workflowObject.getString("projectWorkflowType").equals("BUILD")) {
				workflowId = workflowObject.getInt("workflowId");
				break;
			}
		}

		return workflowId;
	}

	private int getWorkflowVersionId(String workflowJson) {
		org.json.JSONObject workflowJSON = new org.json.JSONObject(workflowJson);

		org.json.JSONArray workflowVersionsArray = workflowJSON.getJSONArray("workflowVersions");

		int workflowVersionId = -1;

		for (int i = 0; i < workflowVersionsArray.length(); i++) {
			org.json.JSONObject workflowVersionObject = workflowVersionsArray.getJSONObject(i);

			if (workflowVersionObject.getString("workflowVersion").equals(mWorkflowVersionOverride)) {
				workflowVersionId = workflowVersionObject.getInt("workflowVersionId");
				break;
			}
		}

		return workflowVersionId;
	}

	private int getWorkflowVersionOverrideId(int projectId) throws Exception {
		org.json.JSONObject projectObject = getProjectById(projectId);

		int workflowId = getWorkflowId(projectObject);

		String url = removeEndSlash(mUrl);
		url = url + PluginConstants.URL_SUFFIX_GET_WORKFLOW + workflowId;

		CloseableHttpClient httpClient = HttpClients.createDefault();
		HttpGet request = new HttpGet(url);

		int returnCode = 0;
		int workflowVersionId = -1;

		try {
			request.addHeader(new BasicScheme().authenticate(creds, request, null));
			CloseableHttpResponse response = httpClient.execute(request);
			String result = EntityUtils.toString(response.getEntity());
			returnCode = response.getStatusLine().getStatusCode();

			if (returnCode == 200) {
				workflowVersionId = getWorkflowVersionId(result);
			} else {
				throw new AbortException(
						"Request failed. Could not get workflow version override id from " + mWorkflowVersionOverride);
			}
		} catch (Exception e) {
			LOG.println(e);
		} finally {
			httpClient.close();
		}

		return workflowVersionId;
	}

	private void buildAuth(Credential pCredential) {
		String userName;
		String password;

		if (pCredential.isUseGlobalCredential()) {
			LOG.println("Looking up global credentials.");
			StandardUsernamePasswordCredentials cred = Credential
					.lookupSystemCredentials(pCredential.getCredentialsId());
			userName = cred.getUsername();
			password = cred.getPassword().getPlainText();
		} else {
			userName = pCredential.getUsername();
			password = pCredential.getPassword().getPlainText();

		}

		LOG.println("Building authentication object.");
		creds = new UsernamePasswordCredentials(userName, password);
	}

	private JSONObject buildJSONBody() throws Exception {
		JSONObject json = new JSONObject();

		int projectId = getProjectId();

		json.put(PluginConstants.JSON_PROJECT_ID, projectId);
		json.put(PluginConstants.JSON_ENVIRONMENT_ID, getEnvironmentId());
		json.put(PluginConstants.JSON_FORCE, mForce);
		json.put(PluginConstants.JSON_PROJECT_STREAM_ID, getProjectStreamId(projectId));

		if (null != mReleaseName && !mReleaseName.isEmpty()) {
			json.put(PluginConstants.JSON_RELEASE_DEF_ID, getReleaseId(projectId));
		}
		if (null != mPackageName && !mPackageName.isEmpty()) {
			json.put(PluginConstants.JSON_PACKAGE_NAME, mPackageName);
		}
		if (null != mWorkflowVersionOverride && !mWorkflowVersionOverride.isEmpty()) {
			json.put(PluginConstants.JSON_WORKFLOW_OVERRIDE_ID, getWorkflowVersionOverrideId(projectId));
		}

		if (null != issueNumbers && !issueNumbers.isEmpty()) {
			json.put("issueNumbers", Arrays.asList(this.issueNumbers.split(",")));
		}

		if (null != inputs && !inputs.isEmpty()) {
			JSONArray inputsArray = new JSONArray();

			List<KeyValuePair> kvp = resolveInputs();
			for (KeyValuePair pair : kvp) {
				JSONObject currentPair = new JSONObject();
				currentPair.put("code", pair.getKey());
				currentPair.put("value", getInputOrVar(pair.getValue())); // Hopefully an easy enough way to get at
																			// environment variables
				inputsArray.add(currentPair);
			}

			json.put("inputs", inputsArray);

		}

		if (null != flexFields && !flexFields.isEmpty()) {
			JSONArray flexFieldsArray = new JSONArray();

			List<KeyValuePair> kvp = resolveFlexFields();
			for (KeyValuePair pair : kvp) {
				JSONObject currentPair = new JSONObject();
				currentPair.put("code", pair.getKey());
				currentPair.put("value", getInputOrVar(pair.getValue()));
				flexFieldsArray.add(currentPair);
			}

			json.put("flexFields", flexFieldsArray);

		}

		if (null != buildFileInputs && !buildFileInputs.isEmpty()) {
			JSONArray buildFileInputsArray = new JSONArray();

			List<BuildFileInput> BFIs = resolveBuildFileInputs();
			for (BuildFileInput bfi : BFIs) {
				JSONObject currentPair = new JSONObject();
				currentPair.put("projectObjectId", bfi.getProjectObjectId());
				currentPair.put("scmRevision", bfi.getScmRevision());
				currentPair.put("fromPackageObjectId", bfi.getFromPackageObjectId());
				buildFileInputsArray.add(currentPair);
			}

			json.put("buildFileInputs", buildFileInputsArray);

		}

		return json;
	}

	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
		private String mUrl;
		private String mProjectStreamName;
		private String mPackageName;
		private String mReleaseName;
		private String mProjectPath;
		private String mEnvironmentCode;
		private String mWorkflowVersionOverride;
		private Boolean mForce;
		private Boolean mWait;

		private String issueNumbers;
		private List<BuildFileInput> buildFileInputs = new ArrayList<>();
		private List<KeyValuePair> inputs = new ArrayList<>();
        private List<KeyValuePair> flexFields = new ArrayList<>();
		private List<Credential> credentials = new ArrayList<>();
		private Credential credential;

		public DescriptorImpl() {
			load();
		}

		public ListBoxModel doFillCredentialItems() {
			ListBoxModel m = new ListBoxModel();
			for (Credential c : credentials)
				m.add(c.getName(), c.getName());
			return m;
		}

		public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context) {
			List<StandardUsernamePasswordCredentials> creds = lookupCredentials(
					StandardUsernamePasswordCredentials.class, context, ACL.SYSTEM, PluginConstants.HTTP_SCHEME,
					PluginConstants.HTTPS_SCHEME);

			return new StandardUsernameListBoxModel().withAll(creds);
		}

		@Override
		public String getDisplayName() {
			return "Trigger FlexDeploy Project";
		}

		@Override
		public boolean isApplicable(Class type) {
			return true;
		}

		@Override
		public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
			setUrl(formData.getString("mUrl"));
			setProjectStreamName(formData.toString());
			setPackageName(formData.getString("mPackageName"));
			setReleaseName(formData.getString("mReleaseName"));
			setProjectPath(formData.getString("mProjectPath"));
			setEnvironmentCode(formData.getString("mEnvironmentCode"));
			setWorkflowVersionOverride(formData.getString("mWorkflowVersionOverride"));
			setForce(formData.getBoolean("mForce"));
			setWait(formData.getBoolean("mWait"));

			save();
			return super.configure(req, formData);
		}

		public FormValidation doValidateUserNamePassword(@QueryParameter String mUrl, @QueryParameter String username,
				@QueryParameter Secret password) {
			try {
				String serverUrl = mUrl;

				if (Strings.isNullOrEmpty(serverUrl)) {
					return FormValidation.error("No server URL specified");
				}

				return validateConnection(serverUrl, username, password.getPlainText());
			} catch (IllegalStateException e) {
				return FormValidation.error(e.getMessage());
			} catch (Exception e) {
				return FormValidation.error("FlexDeploy credentials are invalid. " + e.getMessage());
			}
		}

		public FormValidation doValidateCredential(@QueryParameter String mUrl, @QueryParameter String credentialsId) {
			try {

				if (Strings.isNullOrEmpty(credentialsId)) {
					return FormValidation.error("No credentials specified");
				}

				StandardUsernamePasswordCredentials systemCredentials = Credential
						.lookupSystemCredentials(credentialsId);

				if (systemCredentials == null) {
					return FormValidation.error("Could not find credential with id " + credentialsId);
				}

				if (Strings.isNullOrEmpty(mUrl)) {
					return FormValidation.error("No URL specified");
				}

				return validateConnection(mUrl, systemCredentials.getUsername(),
						systemCredentials.getPassword().getPlainText());
			} catch (IllegalStateException e) {
				return FormValidation.error(e.getMessage());
			} catch (Exception e) {
				return FormValidation.error("FlexDeploy connection failed. " + e.getMessage());
			}
		}

		private static FormValidation validateConnection(String serverUrl, String username, String password) {
			String url = removeEndSlash(serverUrl) + PluginConstants.URL_SUFFIX_GET_WORKFLOW_REQUEST + "/1";

			CloseableHttpClient httpClient = HttpClients.createDefault();
			HttpGet request = new HttpGet(url);

			try
			{
				UsernamePasswordCredentials creds
						= new UsernamePasswordCredentials(username, password);
				request.addHeader(new BasicScheme().authenticate(creds, request, null));
				CloseableHttpResponse response = httpClient.execute(request);
				String result = EntityUtils.toString(response.getEntity());
				int returnCode = response.getStatusLine().getStatusCode();

				httpClient.close();

				boolean idNotFound = result.contains(PluginConstants.ERROR_ID_NOT_FOUND);

				if (returnCode == 404 && !idNotFound)
				{
					return FormValidation.error(
							"The server responded, but FlexDeploy was not found. Make sure the FlexDeploy URL is formatted correctly.");
				}

				if (!result.isEmpty() && (idNotFound || result.contains(PluginConstants.ERROR_NULL_POINTER)))
				{
					return FormValidation.ok("Connected to FlexDeploy, and your credentials are valid!");
				}
				else if(returnCode == 401)
				{
					return FormValidation.error("Connected to FlexDeploy, but your credentials were invalid.");
				}
				else if(returnCode == 403)
				{
					return FormValidation.error("Connected to FlexDeploy, but credentials have invalid authorization");
				}
				else if(result.isEmpty())
				{
					return FormValidation.error("Server gave HTTP return code [" + returnCode + "].");
				}
			}
			catch (UnknownHostException uhe)
			{
				return FormValidation.error("Could not contact host [" + uhe.getMessage() + "]");
			}
			catch (ConnectTimeoutException cte)
			{
				return FormValidation.error("[" + serverUrl + "] failed to respond.");
			}
			catch (Exception e)
			{
				return FormValidation.error(e.getMessage());
			}

			return FormValidation.error("Could not check connection to FlexDeploy.");

		}

		public String getUrl() {
			return mUrl;
		}

		public void setUrl(String mUrl) {
			this.mUrl = mUrl;
		}

		public String getProjectStreamName() {
			return mProjectStreamName;
		}

		public void setProjectStreamName(String mProjectStreamName) {
			this.mProjectStreamName = mProjectStreamName;
		}

		public String getPackageName() {
			return mPackageName;
		}

		public void setPackageName(String mPackageName) {
			this.mPackageName = mPackageName;
		}

		public String getReleaseName() {
			return mReleaseName;
		}

		public void setReleaseName(String mReleaseName) {
			this.mReleaseName = mReleaseName;
		}

		public String getProjectPath() {
			return mProjectPath;
		}

		public void setProjectPath(String mProjectPath) {
			while (mProjectPath != null && mProjectPath.startsWith("/")) {
				mProjectPath = mProjectPath.substring(1);
			}
			this.mProjectPath = mProjectPath;
		}

		public String getEnvironmentCode() {
			return mEnvironmentCode;
		}

		public void setEnvironmentCode(String mEnvironmentCode) {
			this.mEnvironmentCode = mEnvironmentCode.toUpperCase();
			;
		}

		public String getWorkflowVersionOverride() {
			return mWorkflowVersionOverride;
		}

		public void setWorkflowVersionOverride(String mWorkflowVersionOverride) {
			this.mWorkflowVersionOverride = mWorkflowVersionOverride;
		}

		public Boolean getForce() {
			return mForce;
		}

		public void setForce(Boolean mForce) {
			this.mForce = mForce;
		}

		public Boolean getWait() {
			return mWait;
		}

		public void setWait(Boolean mWait) {
			this.mWait = mWait;
		}

		public String getIssueNumbers() {
			return issueNumbers;
		}

		public void setIssueNumbers(String issueNumbers) {
			this.issueNumbers = issueNumbers;
		}

		public List<BuildFileInput> getBuildFileInputs() {
			return buildFileInputs;
		}

		public void setBuildFileInputs(List<BuildFileInput> buildFileInputs) {
			this.buildFileInputs = buildFileInputs;
		}

		public List<KeyValuePair> getInputs() {
			return inputs;
		}

		public void setInputs(List<KeyValuePair> inputs) {
			this.inputs = inputs;
		}

		public List<Credential> getCredentials() {
			return credentials;
		}

		public void setCredentials(List<Credential> credentials) {
			this.credentials = credentials;
		}

		public Credential getCredential() {
			return credential;
		}

		public void setCredential(Credential credential) {
			this.credential = credential;
		}

		public List<KeyValuePair> getFlexFields() {
			return flexFields;
		}

		public void setFlexFields(List<KeyValuePair> flexFields) {
			this.flexFields = flexFields;
		}

	}

	@Override
	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE; // This can also be set to BUILD, which
										// will only allow it to execute after
										// the previous build completes. NONE is
										// more efficient as per doc
	}
}
