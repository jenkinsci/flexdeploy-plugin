package flexagon.fd.plugin.jenkins.test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.UnknownHostException;

import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;

import com.google.common.net.HttpHeaders;

import net.sf.json.JSONObject;

public class JenkinsTest
{

	public static void main(String[] args) throws Exception
	{
		JSONObject json = new JSONObject();
		JSONObject auth = new JSONObject();

		auth.put("userId", "fdadmin");
		auth.put("password", "welcome1");

		json.put("authentication", auth);
		json.put("workflowRequestId", 1);

		String url = "http://fdtlt01.flexagon:8080/flexdeploy/";

		url = url + "/rest/workflow/getWorkflowRequestStatus";

		HttpClient client = new HttpClient();
		BufferedReader br = null;
		PostMethod method = new PostMethod(url);

		method.addRequestHeader(HttpHeaders.CONTENT_TYPE, "application/json");
		method.setRequestBody(json.toString());
		client.setConnectionTimeout(5000);

		try
		{
			int returnCode = client.executeMethod(method);
			System.out.println(returnCode);
			if (returnCode == 404)
			{
				System.out.println("Server URL was bad");
			}

			br = new BufferedReader(new InputStreamReader(method.getResponseBodyAsStream()));
			String readLine;

			while (((readLine = br.readLine()) != null))
			{
				System.out.println(readLine);
				if (readLine.contains("Login failure"))
				{
					System.out.println("Authentication Failed.");
				}
				else if (readLine.contains("WorkflowRequestId [1] not found"))
				{
					System.out.println("Validation Successful");
				}

			}

		}
		catch (UnknownHostException uhe)
		{
			System.out.println("Unknown Host [" + uhe.getMessage() + "]");
		}
		catch (ConnectTimeoutException te)
		{
			System.out.println("Failed to connect to host.");
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
					fe.printStackTrace();
				}

		}
	}

}
