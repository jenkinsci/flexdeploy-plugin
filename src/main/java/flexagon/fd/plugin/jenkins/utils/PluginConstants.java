package flexagon.fd.plugin.jenkins.utils;

import com.cloudbees.plugins.credentials.domains.SchemeRequirement;

public class PluginConstants
{
	public static final SchemeRequirement HTTP_SCHEME = new SchemeRequirement("http");
	public static final SchemeRequirement HTTPS_SCHEME = new SchemeRequirement("https");

	//JSON Body Properties
	public static final String CONTENT_TYPE_APP_JSON = "application/json";
	public static final String JSON_AUTHENTICATION = "authentication";
	public static final String JSON_USER_ID = "userId";
	public static final String JSON_PASSWORD = "password";
	public static final String JSON_WORKFLOW_REQUEST_ID = "workflowRequestId";
	public static final String JSON_ENVIRONMENT_CODE = "environmentCode";
	public static final String JSON_STREAM_NAME = "streamName";
	public static final String JSON_PROJECT_PATH = "qualifiedProjectName";
	public static final String JSON_FORCE_BUILD = "forceBuild";

	//URL Properties
	public static final String URL_SUFFIX_WORKFLOW_STATUS = "/rest/workflow/getWorkflowRequestStatus";
	public static final String URL_SUFFIX_BUILD_PROJECT = "/rest/workflow/buildProject";

	//Workflow Status
	public static final String WORKFLOW_STATUS_FAIL = "FAILED";
	public static final String WORKFLOW_STATUS_COMPLETED = "COMPLETED";

	//Timeouts
	public static final int TIMEOUT_WORKFLOW_EXECUTION = 15; //Minutes
	public static final int TIMEOUT_CONNECTION_VALIDATION = 5000; //Millis

	public static final String ERROR_LOGIN_FAILURE = "Login failure";
	public static final String ERROR_ID_NOT_FOUND = "WorkflowRequestId [1] not found";

	private PluginConstants()
	{
		throw new IllegalAccessError("Utility class");
	}
}
