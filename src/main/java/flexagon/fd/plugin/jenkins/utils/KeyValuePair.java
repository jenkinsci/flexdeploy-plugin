package flexagon.fd.plugin.jenkins.utils;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;

/**
 * @author Ben Hoffman
 */
public class KeyValuePair extends AbstractDescribableImpl<KeyValuePair>
{

	private final String key;
	private final String value;

	@DataBoundConstructor
	public KeyValuePair(String key, String value)
	{
		this.key = key;
		this.value = value;
	}

	public String getKey()
	{
		return key;
	}

	public String getValue()
	{
		return value;
	}

	@Extension
	public static class KeyValuePairDescriptor extends Descriptor<KeyValuePair>
	{

		public FormValidation doCheckName(@QueryParameter String value)
		{
			return FormValidation.validateRequired(value);
		}

		public FormValidation doCheckValue(@QueryParameter String value)
		{
			return FormValidation.validateRequired(value);
		}

		@Override
		public String getDisplayName()
		{
			return "";
		}
	}

}
