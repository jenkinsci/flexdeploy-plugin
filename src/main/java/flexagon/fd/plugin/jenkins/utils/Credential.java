package flexagon.fd.plugin.jenkins.utils;

import static com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials;

import java.util.List;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Project;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;

public class Credential extends AbstractDescribableImpl<Credential>
{

	public static final Function<Credential, String> CREDENTIAL_INDEX = new Function<Credential, String>()
	{
		@Override
		public String apply(Credential input)
		{
			String name = "";
			if(null != input)
			{
				name = input.getName();
			}
			return name;
		}

	private final String name;
	private final String username;
	private final Secret password;
	private final String credentialsId;
	private final boolean useGlobalCredential;

	@DataBoundConstructor
	public Credential(String name, String username, Secret password, String credentialsId, boolean useGlobalCredential)
	{
		this.name = name;
		this.username = username;
		this.password = password;
		this.credentialsId = credentialsId;
		this.useGlobalCredential = useGlobalCredential;
	}

	public String getCredentialsId()
	{
		return credentialsId;
	}

	public String getKey()
	{
		return username + ":" + password.getPlainText() + "@" + name + ":" + credentialsId + ":";
	}

	public String getName()
	{
		return name;
	}

	public String getUsername()
	{
		return username;
	}

	public Secret getPassword()
	{
		return password;
	}

	public boolean isUseGlobalCredential()
	{
		return useGlobalCredential;
	}

	public boolean showGolbalCredentials()
	{
		return useGlobalCredential;
	}

	@Override
	public String toString()
	{
		return name;
	}

	public static StandardUsernamePasswordCredentials lookupSystemCredentials(String credentialsId)
	{
		return CredentialsMatchers.firstOrNull(
				lookupCredentials(StandardUsernamePasswordCredentials.class, Jenkins.getInstance(), ACL.SYSTEM,
						PluginConstants.HTTP_SCHEME, PluginConstants.HTTPS_SCHEME),
				CredentialsMatchers.withId(credentialsId));
	}

	@Extension
	public static final class CredentialDescriptor extends Descriptor<Credential>
	{
		@Override
		public String getDisplayName()
		{
			return "Credential";
		}

		public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Project context)
		{
			List<StandardUsernamePasswordCredentials> creds = lookupCredentials(
					StandardUsernamePasswordCredentials.class, context, ACL.SYSTEM, PluginConstants.HTTP_SCHEME,
					PluginConstants.HTTPS_SCHEME);

			return new StandardUsernameListBoxModel().withAll(creds);
		}

		public static Credential fromStapler(@QueryParameter String name, @QueryParameter String username,
				@QueryParameter Secret password, @QueryParameter String credentialsId,
				@QueryParameter boolean useGlobalCredential)
		{

			return new Credential(name, username, password, credentialsId, useGlobalCredential);
		}

	}

}