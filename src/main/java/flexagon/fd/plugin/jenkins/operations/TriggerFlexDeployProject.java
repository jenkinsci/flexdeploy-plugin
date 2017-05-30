package flexagon.fd.plugin.jenkins.operations;

import static com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials;

import java.io.IOException;
import java.io.PrintStream;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.google.common.base.Strings;
import com.google.common.net.HttpHeaders;

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
 * @author Ben Hoffman
 */
public final class TriggerFlexDeployProject extends Notifier
{
	private final String fdUrl;
	private final String fdEnvCode;
	private final String fdProjectPath;
	private final String fdStreamName;
	private final Boolean fdWait;

	private List<KeyValuePair> inputs = new ArrayList<>();
	private List<KeyValuePair> flexFields = new ArrayList<>();
	private final Credential credential;
	private static PrintStream LOG;

	private EnvVars envVars = new EnvVars();
	private JSONObject auth;

	@DataBoundConstructor
	public TriggerFlexDeployProject(String fdUrl, String fdProjectPath, String fdEnvCode, List<KeyValuePair> inputs,
			List<KeyValuePair> flexFields, Credential credential, String fdStreamName, Boolean fdWait)
	{
		this.fdUrl = fdUrl;
		this.fdProjectPath = fdProjectPath;
		this.fdEnvCode = fdEnvCode.toUpperCase();
		this.inputs = inputs;
		this.flexFields = flexFields;
		this.credential = credential;
		this.fdStreamName = fdStreamName;
		this.fdWait = fdWait;

	}

	public String getFdUrl()
	{
		return fdUrl;
	}

	public Boolean getFdWait()
	{
		return fdWait;
	}

	public String getFdStreamName()
	{
		return fdStreamName;
	}

	public Credential getCredential()
	{
		return credential;
	}

	public List<KeyValuePair> getInputs()
	{
		return inputs;
	}

	public List<KeyValuePair> getFlexFields()
	{
		return flexFields;
	}

	public void setFlexFields(List<KeyValuePair> flexFields)
	{
		this.flexFields = flexFields;
	}

	public String getFdProjectPath()
	{
		return fdProjectPath;
	}

	public String getFdEnvCode()
	{
		return fdEnvCode;
	}

	private String getInputOrVar(String pInputValue)
	{
		if (envVars.containsKey(pInputValue))
		{
			return envVars.get(pInputValue);
		}
		else
		{
			return pInputValue;
		}

	}

	private static void setLogger(BuildListener listener)
	{
		LOG = listener.getLogger();
	}

	@Override
	public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener)
	{
		setLogger(listener);
		String workflowStatus;

		LOG.println("Building authentication object...");
		auth = buildAuth(credential);

		try
		{
			LOG.println("Getting env vars...");
			envVars = build.getEnvironment(listener);

			LOG.println("Executing REST request to FlexDeploy.");
			workflowStatus = executeRequest();

			if (workflowStatus.equals(PluginConstants.WORKFLOW_STATUS_COMPLETED))
			{
				LOG.println("Execution completed successfully.");
				return true;
			}
			else
			{
				LOG.println("Execution failed.");
				return false;
			}

		}
		catch (Exception e)
		{
			LOG.println("Unknown error has occurred. " + e);
			return false;
		}

	}

	private List<KeyValuePair> resolveInputs() //populates the Input list exactly the same way as FlexFields
	{
		List<KeyValuePair> kvp = new ArrayList<>();

		for (KeyValuePair pair : inputs)
		{
			String key = pair.getKey();
			String value = pair.getValue();

			kvp.add(new KeyValuePair(key, value));
		}

		LOG.println("Added " + kvp.size() + " inputs to body.");

		return kvp;
	}

	private List<KeyValuePair> resolveFlexFields() //populates the FlexField list exactly the same way as inputs
	{
		List<KeyValuePair> kvp = new ArrayList<>();

		for (KeyValuePair pair : flexFields)
		{
			String key = pair.getKey();
			String value = pair.getValue();

			kvp.add(new KeyValuePair(key, value));
		}

		LOG.println("Added " + kvp.size() + " FlexFields to body.");

		return kvp;
	}

	private static String removeEndSlash(String pUrl)
	{
		String s = pUrl;

		while (s.endsWith("/") || s.endsWith("\\"))
		{
			s = s.substring(0, s.length() - 1);
		}

		return s;
	}

	private String waitForFlexDeploy(final String pWorkflowId)
	{
		ExecutorService executor = Executors.newSingleThreadExecutor();
		final Future<String> future = executor.submit(new Callable()
		{
			@Override
			public String call() throws Exception
			{
				String status = getWorkflowExecutionStatus(pWorkflowId);

				while (!status.equals(PluginConstants.WORKFLOW_STATUS_COMPLETED)
						&& !status.equals(PluginConstants.WORKFLOW_STATUS_FAIL))
				{
					LOG.println("Workflow execution status is " + status + ". Checking again in 5 seconds...");
					Thread.sleep(5000);
					status = getWorkflowExecutionStatus(pWorkflowId);
				}

				return status;
			}
		});

		try
		{
			return future.get(PluginConstants.TIMEOUT_WORKFLOW_EXECUTION, TimeUnit.MINUTES);

		}
		catch (TimeoutException | InterruptedException | ExecutionException e)
		{
			future.cancel(true);
			LOG.println("Workflow execution is taking too long, stopping plugin execution with success.");
			LOG.println(e);
			return PluginConstants.WORKFLOW_STATUS_COMPLETED;
		}
		finally
		{
			executor.shutdownNow();
		}

	}

	private String executeRequest() throws AbortException
	{
		LOG.println("Building JSON Body...");
		JSONObject body = buildJSONBody();

		String url = removeEndSlash(fdUrl);
		url = url + PluginConstants.URL_SUFFIX_BUILD_PROJECT;

		HttpClient client = new HttpClient();

		PostMethod method = new PostMethod(url);

		LOG.println("Setting Headers");
		method.addRequestHeader(HttpHeaders.CONTENT_TYPE, PluginConstants.CONTENT_TYPE_APP_JSON);

		LOG.println("Setting Body");
		method.setRequestBody(body.toString());

		int returnCode = 0;

		try
		{
			LOG.println("Executing Request");
			returnCode = client.executeMethod(method);

			LOG.println("Return code was: " + returnCode + " : " + method.getStatusText());

			String workflowId = method.getResponseBodyAsString();
			if (!Strings.isNullOrEmpty(workflowId) && returnCode == 201)
			{
				LOG.println("Successfully got workflow Id.");
				if (fdWait)
				{
					return waitForFlexDeploy(workflowId);
				}
				else
				{
					return PluginConstants.WORKFLOW_STATUS_COMPLETED;
				}

			}
			else
			{
				throw new AbortException("Request failed. Could not execute workflow.");
			}

		}
		catch (AbortException a)
		{
			throw a;
		}
		catch (IOException e)
		{
			LOG.println("Unknown error occurred in the FlexDeploy REST call. " + e);
			return PluginConstants.WORKFLOW_STATUS_FAIL;
		}

		finally
		{
			method.releaseConnection();
		}

	}

	private String getWorkflowExecutionStatus(String pWorkflowId)
	{
		String url = removeEndSlash(fdUrl);
		url = url + PluginConstants.URL_SUFFIX_WORKFLOW_STATUS;

		HttpClient client = new HttpClient();

		PostMethod method = new PostMethod(url);

		method.addRequestHeader(HttpHeaders.CONTENT_TYPE, PluginConstants.CONTENT_TYPE_APP_JSON);

		JSONObject json = new JSONObject();

		json.put(PluginConstants.JSON_AUTHENTICATION, auth);
		json.put(PluginConstants.JSON_WORKFLOW_REQUEST_ID, pWorkflowId);

		method.setRequestBody(json.toString());
		String workflowStatus = PluginConstants.WORKFLOW_STATUS_COMPLETED;
		int responseCode = 0;

		try
		{
			responseCode = client.executeMethod(method);
			if (responseCode == 201)
			{
				workflowStatus = method.getResponseBodyAsString();
			}
		}
		catch (Exception e)
		{
			LOG.println("WARNING: Failed to check workflow status. " + e);
		}
		finally
		{
			method.releaseConnection();
		}

		return workflowStatus;
	}

	private JSONObject buildAuth(Credential pCredential)
	{
		String userName;
		String password;

		auth = new JSONObject();

		if (pCredential.isUseGlobalCredential())
		{
			LOG.println("Looking up global credentials.");
			StandardUsernamePasswordCredentials cred = Credential
					.lookupSystemCredentials(pCredential.getCredentialsId());
			userName = cred.getUsername();
			password = cred.getPassword().getPlainText();
		}
		else
		{
			userName = pCredential.getUsername();
			password = pCredential.getPassword().getPlainText();

		}

		LOG.println("Building authentication object.");
		auth.put(PluginConstants.JSON_USER_ID, userName);
		auth.put(PluginConstants.JSON_PASSWORD, password);

		return auth;
	}

	private JSONObject buildJSONBody() throws AbortException
	{
		JSONObject json = new JSONObject();

		if (!auth.isEmpty() && !auth.isNullObject())
		{
			json.put(PluginConstants.JSON_AUTHENTICATION, auth);
		}
		else
		{
			LOG.println("Authentication was empty!");
			throw new AbortException("The authentication object was empty. Did you select credentials?");
		}

		json.put(PluginConstants.JSON_ENVIRONMENT_CODE, fdEnvCode);
		json.put(PluginConstants.JSON_PROJECT_PATH, fdProjectPath);
		json.put(PluginConstants.JSON_STREAM_NAME, fdStreamName);
		json.put(PluginConstants.JSON_FORCE_BUILD, Boolean.TRUE);//Always force to avoid errors. 
		if (null != inputs && !inputs.isEmpty())
		{
			JSONArray inputsArray = new JSONArray();

			List<KeyValuePair> kvp = resolveInputs();
			for (KeyValuePair pair : kvp)
			{
				JSONObject currentPair = new JSONObject();
				currentPair.put("code", pair.getKey());
				currentPair.put("value", getInputOrVar(pair.getValue())); //Hopefully an easy enough way to get at environment variables
				inputsArray.add(currentPair);
			}

			json.put("inputs", inputsArray);

		}

		if (null != flexFields && !flexFields.isEmpty())
		{
			JSONArray flexFieldsArray = new JSONArray();

			List<KeyValuePair> kvp = resolveFlexFields();
			for (KeyValuePair pair : kvp)
			{
				JSONObject currentPair = new JSONObject();
				currentPair.put("code", pair.getKey());
				currentPair.put("value", getInputOrVar(pair.getValue()));
				flexFieldsArray.add(currentPair);
			}

			json.put("flexFields", flexFieldsArray);

		}

		return json;
	}

	@Override
	public DescriptorImpl getDescriptor()
	{
		return (DescriptorImpl) super.getDescriptor();
	}

	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Publisher>
	{
		private String fdUrl;
		private String envCode;
		private String projectPath;
		private String credentialsId;
		private String fdStreamName;
		private Boolean fdWait;
		private List<KeyValuePair> inputs = new ArrayList<>();
		private List<KeyValuePair> flexFields = new ArrayList<>();
		private List<Credential> credentials = new ArrayList<>();
		private Credential credential;

		public DescriptorImpl()
		{
			load();
		}

		public ListBoxModel doFillCredentialItems()
		{
			ListBoxModel m = new ListBoxModel();
			for (Credential c : credentials)
				m.add(c.getName(), c.getName());
			return m;
		}

		public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context)
		{
			List<StandardUsernamePasswordCredentials> creds = lookupCredentials(
					StandardUsernamePasswordCredentials.class, context, ACL.SYSTEM, PluginConstants.HTTP_SCHEME,
					PluginConstants.HTTPS_SCHEME);

			return new StandardUsernameListBoxModel().withAll(creds);
		}

		@Override
		public String getDisplayName()
		{
			return "Trigger FlexDeploy Project";
		}

		@Override
		public boolean isApplicable(Class type)
		{
			return true;
		}

		@Override
		public boolean configure(StaplerRequest staplerRequest, JSONObject json) throws FormException
		{

			setFlexDeployUrl(json.getString("fdUrl"));
			setEnvCode(json.getString("fdEnvCode"));
			setProjectPath(json.getString("fdProjectPath"));
			setFdStreamName(json.getString("fdStreamName"));
			setFdWait(json.getBoolean("fdWait"));

			save();
			return true;
		}

		public FormValidation doValidateUserNamePassword(@QueryParameter String fdUrl, @QueryParameter String username,
				@QueryParameter Secret password)
		{
			try
			{
				String serverUrl = fdUrl;

				if (Strings.isNullOrEmpty(serverUrl))
				{
					return FormValidation.error("No server URL specified");
				}

				return validateConnection(serverUrl, username, password.getPlainText());
			}
			catch (IllegalStateException e)
			{
				return FormValidation.error(e.getMessage());
			}
			catch (Exception e)
			{
				return FormValidation.error("FlexDeploy credentials are invalid. " + e.getMessage());
			}
		}

		public FormValidation doValidateCredential(@QueryParameter String fdUrl, @QueryParameter String credentialsId)
		{
			try
			{

				if (Strings.isNullOrEmpty(credentialsId))
				{
					return FormValidation.error("No credentials specified");
				}

				StandardUsernamePasswordCredentials systemCredentials = Credential
						.lookupSystemCredentials(credentialsId);

				if (systemCredentials == null)
				{
					return FormValidation.error("Could not find credential with id " + credentialsId);
				}

				if (Strings.isNullOrEmpty(fdUrl))
				{
					return FormValidation.error("No URL specified");
				}

				return validateConnection(fdUrl, systemCredentials.getUsername(),
						systemCredentials.getPassword().getPlainText());
			}
			catch (IllegalStateException e)
			{
				return FormValidation.error(e.getMessage());
			}
			catch (Exception e)
			{
				return FormValidation.error("FlexDeploy connection failed. " + e.getMessage());
			}
		}

		private FormValidation validateConnection(String serverUrl, String username, String password)
		{
			String url = removeEndSlash(serverUrl) + PluginConstants.URL_SUFFIX_WORKFLOW_STATUS;

			JSONObject json = new JSONObject();
			JSONObject auth = new JSONObject();

			auth.put(PluginConstants.JSON_USER_ID, username);
			auth.put(PluginConstants.JSON_PASSWORD, password);

			json.put(PluginConstants.JSON_AUTHENTICATION, auth);
			json.put(PluginConstants.JSON_WORKFLOW_REQUEST_ID, 1);

			HttpClient client = new HttpClient();

			PostMethod method = new PostMethod(url);

			client.setConnectionTimeout(PluginConstants.TIMEOUT_CONNECTION_VALIDATION);
			method.addRequestHeader(HttpHeaders.CONTENT_TYPE, PluginConstants.CONTENT_TYPE_APP_JSON);
			method.setRequestBody(json.toString());

			try
			{
				int returnCode = client.executeMethod(method);
				String response = method.getResponseBodyAsString();
				if (returnCode == 404)
				{
					return FormValidation.error(
							"The server responded, but FlexDeploy was not found. Make sure the FlexDeploy URL is formatted correctly.");
				}
				else
				{
					if (response.contains(PluginConstants.ERROR_LOGIN_FAILURE))
					{
						return FormValidation.error("Connected to FlexDeploy, but your credentials were invalid.");
					}
					else if (response.contains(PluginConstants.ERROR_ID_NOT_FOUND))
					{
						return FormValidation.ok("Connected to FlexDeploy, and your credentials are valid!");
					}
					else
					{
						return FormValidation.error(
								"Failed to check credentials. This does not necessarily mean that the username/password were invalid, but that the test couldn't run. Possibly due to a timeout.");
					}
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
			finally
			{
				method.releaseConnection();
			}

		}

		public String getFdUrl()
		{
			return fdUrl;
		}

		public Boolean getFdWait()
		{
			return fdWait;
		}

		public void setFdWait(Boolean fdWait)
		{
			this.fdWait = fdWait;
		}

		public String getFdStreamName()
		{
			return fdStreamName;
		}

		public void setFdStreamName(String fdStreamName)
		{
			this.fdStreamName = fdStreamName;
		}

		public void setFlexDeployUrl(String flexDeployUrl)
		{
			this.fdUrl = flexDeployUrl;
		}

		public String getEnvCode()
		{
			return envCode;
		}

		public void setEnvCode(String envCode)
		{
			this.envCode = envCode.toUpperCase();
		}

		public String getProjectPath()
		{
			return projectPath;
		}

		public void setProjectPath(String projectPath)
		{
			this.projectPath = projectPath;
		}

		public String getCredentialsId()
		{
			return credentialsId;
		}

		public void setCredentialsId(String credentialsId)
		{
			this.credentialsId = credentialsId;
		}

		public List<KeyValuePair> getInputs()
		{
			return inputs;
		}

		public void setInputs(List<KeyValuePair> inputs)
		{
			this.inputs = inputs;
		}

		public List<Credential> getCredentials()
		{
			return credentials;
		}

		public void setCredentials(List<Credential> credentials)
		{
			this.credentials = credentials;
		}

		public Credential getcredential()
		{
			return credential;
		}

		public void setcredential(Credential credential)
		{
			this.credential = credential;
		}

		public List<KeyValuePair> getFlexFields()
		{
			return flexFields;
		}

		public void setFlexFields(List<KeyValuePair> flexFields)
		{
			this.flexFields = flexFields;
		}

	}

	@Override
	public BuildStepMonitor getRequiredMonitorService()
	{
		return BuildStepMonitor.NONE; // This can also be set to BUILD, which
										// will only allow it to execute after
										// the previous build completes. NONE is
										// more efficient as per doc
	}
}
