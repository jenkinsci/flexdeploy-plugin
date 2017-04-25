package flexagon.fd.plugin.jenkins.operations;

import static com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.SchemeRequirement;
import com.google.common.base.Strings;
import com.google.common.net.HttpHeaders;

import flexagon.fd.plugin.jenkins.utils.Credential;
import flexagon.fd.plugin.jenkins.utils.KeyValuePair;
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
public final class BuildProject extends Notifier
{
	public static final String WORKFLOW_STATUS_FAIL = "FAILED";
	public static final String WORKFLOW_STATUS_COMPLETED = "COMPLETED";

	private final String fdUrl;
	private final String fdEnvCode;
	private final String fdProjectPath;
	private final String fdStreamName;
	private final Boolean fdWait;

	//private final String credentialsId;
	private List<KeyValuePair> inputs = new ArrayList<KeyValuePair>();
	private List<KeyValuePair> flexFields = new ArrayList<KeyValuePair>();
	private final Credential credential;
	public BuildListener listener;

	private EnvVars envVars = new EnvVars();
	private JSONObject auth;

	// config.jelly must have the parameter names from this constructor
	@DataBoundConstructor
	public BuildProject(String fdUrl, String fdProjectPath, String fdEnvCode, List<KeyValuePair> inputs,
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

	public String getInputOrVar(String pInputValue)
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

	@Override
	public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener)
	{
		this.listener = listener;
		JSONObject body;
		String workflowStatus;

		listener.getLogger().println("Building authentication object...");
		auth = buildAuth(credential);

		try
		{
			listener.getLogger().println("Getting env vars...");
			envVars = build.getEnvironment(listener);

			listener.getLogger().println("Building JSON Body...");
			body = buildJSONBody();

			listener.getLogger().println("Executing REST request to FlexDeploy.");
			workflowStatus = executeRequest(body);

			if (workflowStatus.equals(WORKFLOW_STATUS_COMPLETED))
			{
				listener.getLogger().println("Execution completed successfully.");
				return true;
			}
			else
			{
				listener.getLogger().println("Execution failed.");
				return false;
			}

		}
		catch (Exception e)
		{
			e.printStackTrace();
			return false;
		}

	}

	public List<KeyValuePair> resolveInputs() //populates the Input list exactly the same way as FlexFields
	{
		List<KeyValuePair> kvp = new ArrayList<KeyValuePair>();

		for (KeyValuePair pair : inputs)
		{
			String key = pair.getKey();
			String value = pair.getValue();

			kvp.add(new KeyValuePair(key, value));
		}

		listener.getLogger().println("Added " + kvp.size() + " inputs to body.");

		return kvp;
	}

	public List<KeyValuePair> resolveFlexFields() //populates the FlexField list exactly the same way as inputs
	{
		List<KeyValuePair> kvp = new ArrayList<KeyValuePair>();

		for (KeyValuePair pair : flexFields)
		{
			String key = pair.getKey();
			String value = pair.getValue();

			kvp.add(new KeyValuePair(key, value));
		}

		listener.getLogger().println("Added " + kvp.size() + " FlexFields to body.");

		return kvp;
	}

	public static String removeEndSlash(String pUrl)
	{
		String s = pUrl;

		if (pUrl.endsWith("/"))
		{
			s = pUrl.substring(0, pUrl.lastIndexOf("/") - 1);
		}

		return s;
	}

	public String executeRequest(JSONObject pBody) throws Exception
	{
		String finalStatus = WORKFLOW_STATUS_COMPLETED;

		String url = removeEndSlash(fdUrl);
		url = url + "/rest/workflow/buildProject";

		HttpClient client = new HttpClient();
		BufferedReader br = null;
		PostMethod method = new PostMethod(url);

		listener.getLogger().println("Setting Headers");
		method.addRequestHeader(HttpHeaders.CONTENT_TYPE, "application/json");

		listener.getLogger().println("Setting Body");
		method.setRequestBody(pBody.toString());

		int returnCode = 0;

		try
		{
			listener.getLogger().println("Executing Request");
			returnCode = client.executeMethod(method);

			listener.getLogger().println("Return code was: " + returnCode);

			if (returnCode == 501)
			{
				listener.getLogger().println("The Post method is not implemented.");
				method.getResponseBodyAsString();
			}
			else
			{
				//It gets hard to read here.
				br = new BufferedReader(new InputStreamReader(method.getResponseBodyAsStream()));
				String readLine;
				int index = 1; //This is so we only try to get the workflow Id from the first line.

				while (((readLine = br.readLine()) != null))
				{

					if (returnCode == 201 && index == 1) //Confirm call success, and only try this on the first line
					{
						if (fdWait) //Input from UI
						{

							try
							{
								Long workflowId = Long.parseLong(readLine); //first line of response should be the Workflow Id.  
								String status = getWorkflowExecutionStatus(workflowId); //XXX Can we just send in a string?

								//TODO come up with a better way to timeout
								int statusChecks = 1; //different counter for how many status checks we've done. Probably not the best way to set a timeout, but it should work.

								while (!status.equals(WORKFLOW_STATUS_COMPLETED)
										&& !status.equals(WORKFLOW_STATUS_FAIL))
								{
									if (statusChecks >= 180) //After 15 minutes, stop checking, but don't fail.
									{
										listener.getLogger().println(
												"WARNING: Workflow execution is taking longer than 15 minutes. Stopping poller and returning success.");
										status = WORKFLOW_STATUS_COMPLETED;
										break;
									}

									listener.getLogger().println("Workflow execution status is " + status
											+ ". Checking again in 5 seconds...");
									Thread.sleep(5000);
									status = getWorkflowExecutionStatus(workflowId);

								}
								finalStatus = status;

							}
							catch (NumberFormatException nfe)
							{
								listener.getLogger().println("Failed to parse Workflow Id."); //We don't want to throw for this, because we probably read the wrong line
							}

							//end of if
						}

						index++; //so we don't try to read the next line and parse it into Long
					}

				}

			}
		}
		catch (Exception e)
		{
			return WORKFLOW_STATUS_FAIL; //This catch is for the outermost try. If the main call fails, return failure. 
		}
		finally
		{
			method.releaseConnection();
			if (br != null)
				try
				{
					br.close();
				}
				catch (Exception fe)
				{
					listener.getLogger().println("Failed to close the bufferedReader.");
				}
		}

		return finalStatus; //Return success if fdWait is false.
	}

	public String getWorkflowExecutionStatus(Long pWorkflowId) throws Exception
	{
		String url = removeEndSlash(fdUrl);
		url = url + "/rest/workflow/getWorkflowRequestStatus";

		HttpClient client = new HttpClient();
		BufferedReader br = null;
		PostMethod method = new PostMethod(url);

		method.addRequestHeader(HttpHeaders.CONTENT_TYPE, "application/json");

		JSONObject json = new JSONObject();

		json.put("authentication", auth);
		json.put("workflowRequestId", pWorkflowId);

		method.setRequestBody(json.toString());
		String workflowStatus = WORKFLOW_STATUS_COMPLETED;

		try
		{
			client.executeMethod(method);

			br = new BufferedReader(new InputStreamReader(method.getResponseBodyAsStream()));
			String readLine;

			while (((readLine = br.readLine()) != null))
			{
				workflowStatus = readLine;
				return workflowStatus;
			}
		}
		catch (Exception e)
		{
			listener.getLogger().println("WARNING: Failed to check workflow status.");
		}
		finally
		{
			method.releaseConnection();
			if (br != null)
				try
				{
					br.close();
				}
				catch (Exception fe)
				{
					listener.getLogger().println("Failed to close the bufferedReader.");
				}
		}
		return workflowStatus;
	}

	public JSONObject buildAuth(Credential pCredential)
	{
		String userName;
		String password;

		auth = new JSONObject();

		if (pCredential.isUseGlobalCredential())
		{
			listener.getLogger().println("Looking up global credentials.");
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

		listener.getLogger().println("Building authentication object.");
		auth.put("userId", userName);
		auth.put("password", password);

		return auth;
	}

	public JSONObject buildJSONBody() throws Exception
	{
		JSONObject json = new JSONObject();

		//Authentication
		if (!auth.isEmpty() || !auth.isNullObject())
		{
			json.put("authentication", auth);
		}
		else
		{
			listener.getLogger().println("Authentication was empty!");
			Exception e = new Exception("The authentication object was empty. Did you select credentials?");
			throw e;
		}

		// required for build
		json.put("environmentCode", fdEnvCode);
		json.put("qualifiedProjectName", fdProjectPath);
		json.put("streamName", fdStreamName);
		json.put("forceBuild", "true");//Always force to avoid errors. 

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
		private static final SchemeRequirement HTTP_SCHEME = new SchemeRequirement("http");
		private static final SchemeRequirement HTTPS_SCHEME = new SchemeRequirement("https");
		private String fdUrl;
		private String envCode;
		private String projectPath;
		private String credentialsId;
		private String fdStreamName;
		private Boolean fdWait;
		private List<KeyValuePair> inputs = new ArrayList<KeyValuePair>();
		private List<KeyValuePair> flexFields = new ArrayList<KeyValuePair>();
		private List<Credential> credentials = new ArrayList<Credential>();
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
					StandardUsernamePasswordCredentials.class, context, ACL.SYSTEM, HTTP_SCHEME, HTTPS_SCHEME);

			return new StandardUsernameListBoxModel().withAll(creds);
		}

		@Override
		public String getDisplayName()
		{
			return "Build FlexDeploy Project";
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
				@QueryParameter Secret password) throws IOException
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
				return FormValidation.error("FlexDeploy credentials are invalid. %s", e.getMessage());
			}
		}

		public FormValidation doValidateCredential(@QueryParameter String fdUrl, @QueryParameter String credentialsId)
				throws IOException
		{
			try
			{
				String serverUrl = fdUrl;

				if (Strings.isNullOrEmpty(credentialsId))
				{
					return FormValidation.error("No credentials specified");
				}

				StandardUsernamePasswordCredentials credentials = Credential.lookupSystemCredentials(credentialsId);

				if (credentials == null)
				{
					return FormValidation.error("Could not find credential with id " + credentialsId);
				}

				if (Strings.isNullOrEmpty(serverUrl))
				{
					return FormValidation.error("No URL specified");
				}

				return validateConnection(serverUrl, credentials.getUsername(),
						credentials.getPassword().getPlainText());
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

		private FormValidation validateConnection(String serverUrl, String username, String password) throws Exception
		{
			String url = removeEndSlash(serverUrl) + "/rest/workflow/getWorkflowRequestStatus";

			JSONObject json = new JSONObject();
			JSONObject auth = new JSONObject();

			auth.put("userId", username);
			auth.put("password", password);

			json.put("authentication", auth);
			json.put("workflowRequestId", 1);

			HttpClient client = new HttpClient();
			BufferedReader br = null;
			PostMethod method = new PostMethod(url);

			client.setConnectionTimeout(5000);
			method.addRequestHeader(HttpHeaders.CONTENT_TYPE, "application/json");
			method.setRequestBody(json.toString());

			try
			{
				int returnCode = client.executeMethod(method);
				if (returnCode == 404)
				{
					return FormValidation.error(
							"The server responded, but FlexDeploy was not found. Make sure the FlexDeploy URL is formatted correctly.");
				}

				br = new BufferedReader(new InputStreamReader(method.getResponseBodyAsStream()));
				String readLine;

				while (((readLine = br.readLine()) != null))
				{
					if (readLine.contains("Login failure"))
					{
						return FormValidation.error("Connected to FlexDeploy, but your credentials were invalid.");
					}
					else if (readLine.contains("WorkflowRequestId [1] not found"))
					{
						return FormValidation.ok("Connected to FlexDeploy, and your credentials are valid!");
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
				e.printStackTrace();
			}
			finally
			{
				method.releaseConnection();
				if (br != null)
					try
					{
						br.close();
					}
					catch (Exception fe)
					{
					}
			}

			return FormValidation.error(
					"Failed to check credentials. This does not necessarily mean that the username/password were invalid, but that the test couldn't run. Possibly due to a timeout.");

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
