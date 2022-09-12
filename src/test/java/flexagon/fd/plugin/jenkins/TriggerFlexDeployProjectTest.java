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
		String fdProjectPath = "/a/s/d";
		String fdEnvCode = "DEV";
		List<KeyValuePair> inputs = new ArrayList<>();
		List<KeyValuePair> flexFields = new ArrayList<>();
		Credential credential = new Credential(fdEnvCode, fdEnvCode, null, fdEnvCode, false);
		String fdStreamName = "master";
		String fdRelName = null;
		Boolean fdWait = false;
		TriggerFlexDeployProject t = new TriggerFlexDeployProject(fdUrl, fdProjectPath, fdEnvCode, inputs, flexFields,
				credential, fdStreamName, fdRelName, fdWait);
		Assert.assertEquals(fdUrl, t.getFdUrl());
		Assert.assertEquals(fdProjectPath, t.getFdProjectPath());
		Assert.assertEquals(fdEnvCode, t.getFdEnvCode());
		Assert.assertEquals(inputs, t.getInputs());
		Assert.assertEquals(flexFields, t.getFlexFields());
		Assert.assertEquals(credential, t.getCredential());
		Assert.assertEquals(fdStreamName, t.getFdStreamName());
		Assert.assertEquals(fdRelName, t.getFdRelName());
		Assert.assertEquals(fdWait, t.getFdWait());

	}

}