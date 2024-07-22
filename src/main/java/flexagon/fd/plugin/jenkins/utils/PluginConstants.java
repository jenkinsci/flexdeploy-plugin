package flexagon.fd.plugin.jenkins.utils;

import com.cloudbees.plugins.credentials.domains.SchemeRequirement;

public class PluginConstants
{
	public static final SchemeRequirement HTTP_SCHEME = new SchemeRequirement("http");
	public static final SchemeRequirement HTTPS_SCHEME = new SchemeRequirement("https");

	//JSON Body Properties
	public static final String CONTENT_TYPE_APP_JSON = "application/json";
	public static final String JSON_WORKFLOW_REQUEST_ID = "workflowRequestId";
	public static final String JSON_ENVIRONMENT_ID = "environmentId";
	public static final String JSON_FORCE = "force";
	public static final String JSON_PROJECT_ID = "projectId";
	public static final String JSON_PROJECT_STREAM_ID = "projectStreamId";
	public static final String JSON_PROJECT_BRANCH_ID = "projectBranchId";
	public static final String JSON_RELEASE_DEF_ID = "releaseDefId";
	public static final String JSON_WORKFLOW_OVERRIDE_ID = "workflowVersionOverrideId";
	public static final String JSON_PACKAGE_NAME = "packageName";
	public static final String JSON_FOLDER_ID = "folderId";
	public static final String JSON_FOLDER_NAME = "folderName";

	//URL Properties
	public static final String URL_SUFFIX_GET_WORKFLOW_REQUEST = "/rest/v2/workflowrequest";
	public static final String URL_SUFFIX_BUILD_PROJECT = "/rest/v2/workflowrequest/build";
	public static final String URL_SUFFIX_SEARCH_ENVIRONMENT = "/rest/v2/topology/environment?environmentCode=";
	public static final String URL_SUFFIX_GET_PROJECT = "/rest/v2/project";
	public static final String URL_SUFFIX_SEARCH_PROJECT_1 = "?projectName=";
	public static final String URL_SUFFIX_SEARCH_PROJECT_2 = "&folderId=";
	public static final String URL_SUFFIX_SEARCH_BRANCH = "/branch?branchName=";
	public static final String URL_SUFFIX_SEARCH_FOLDER_1 = "/rest/v2/folder?folderName=";
	public static final String URL_SUFFIX_SEARCH_FOLDER_2 = "&parentFolderId=";
	public static final String URL_SUFFIX_SEARCH_RELEASE = "/rest/v1/releases?releaseName=";
    public static final String URL_SUFFIX_GET_WORKFLOW = "/rest/v2/workflow/";

	//Flexdeploy Folder
	public static final int FLEXDEPLOY_FOLDER_ID = 99;

	//Workflow Status
	public static final String WORKFLOW_STATUS_FAILED = "FAILED";
	public static final String WORKFLOW_STATUS_COMPLETED = "COMPLETED";
	public static final String WORKFLOW_STATUS_ABORTED = "ABORTED";
	public static final String WORKFLOW_STATUS_REJECTED= "REJECTED";

	//Timeouts
	public static final int TIMEOUT_WORKFLOW_EXECUTION = 15; //Minutes
	public static final int TIMEOUT_CONNECTION_VALIDATION = 5000; //Millis

	public static final String ERROR_LOGIN_FAILURE = "Login failure";
	public static final String ERROR_ID_NOT_FOUND = "WorkflowRequest not found";
	public static final String ERROR_NULL_POINTER = "java.lang.NullPointerException";

	private PluginConstants()
	{
		throw new IllegalAccessError("Utility class");
	}
}
