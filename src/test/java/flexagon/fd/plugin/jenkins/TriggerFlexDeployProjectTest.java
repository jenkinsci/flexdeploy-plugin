package flexagon.fd.plugin.jenkins;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;

import flexagon.fd.plugin.jenkins.operations.TriggerFlexDeployProject;
import flexagon.fd.plugin.jenkins.utils.Credential;
import flexagon.fd.plugin.jenkins.utils.KeyValuePair;

public class TriggerFlexDeployProjectTest {

	public void testConstructor() {
		String fdUrl = "http://google.com";
		String fdProjectPath = "a/s/d";
		String fdEnvCode = "DEV";
		List<KeyValuePair> inputs = new ArrayList<>();
		List<KeyValuePair> flexFields = new ArrayList<>();
		Credential credential = new Credential(fdEnvCode, fdEnvCode, null, fdEnvCode, false);
		String fdStreamName = "master";
		String fdPackageName = null;
		String fdRelName = null;
		String wfVersionOverride = null;
		String issueNumbers = null;
		Boolean fdWait = false;
		Boolean fdForce = true;
		TriggerFlexDeployProject t = new TriggerFlexDeployProject(fdUrl, fdStreamName, fdPackageName, fdRelName,
				fdProjectPath, fdEnvCode, wfVersionOverride, fdForce, fdWait, issueNumbers, null, inputs, flexFields,
				credential);
		Assert.assertEquals(fdUrl, t.getmUrl());
		Assert.assertEquals(fdProjectPath, t.getmProjectPath());
		Assert.assertEquals(fdEnvCode, t.getmEnvironmentCode());
		Assert.assertEquals(inputs, t.getInputs());
		Assert.assertEquals(flexFields, t.getFlexFields());
		Assert.assertEquals(credential, t.getCredential());
		Assert.assertEquals(fdStreamName, t.getmProjectStreamName());
		Assert.assertEquals(fdRelName, t.getmReleaseName());
		Assert.assertEquals(fdWait, t.getmWait());
		Assert.assertEquals(fdForce, t.getmForce());
		Assert.assertEquals(wfVersionOverride, t.getmWorkflowVersionOverride());

	}

	public void testCanStartWithSlash() {
		String fdUrl = "http://google.com";
		String fdProjectPath = "/a/s/d";
		String fdEnvCode = "DEV";
		List<KeyValuePair> inputs = new ArrayList<>();
		List<KeyValuePair> flexFields = new ArrayList<>();
		Credential credential = new Credential(fdEnvCode, fdEnvCode, null, fdEnvCode, false);
		String fdStreamName = "master";
		String fdPackageName = null;
		String fdRelName = null;
		String wfVersionOverride = null;
		String issueNumbers = null;
		Boolean fdWait = false;
		Boolean fdForce = true;
		TriggerFlexDeployProject t = new TriggerFlexDeployProject(fdUrl, fdStreamName, fdPackageName, fdRelName,
				fdProjectPath, fdEnvCode, wfVersionOverride, fdForce, fdWait, issueNumbers, null, inputs, flexFields,
				credential);
		Assert.assertNotEquals(fdProjectPath, t.getmProjectPath());
		Assert.assertEquals("a/s/d", t.getmProjectPath());
	}

}