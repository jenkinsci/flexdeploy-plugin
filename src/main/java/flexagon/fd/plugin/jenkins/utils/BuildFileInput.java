package flexagon.fd.plugin.jenkins.utils;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;

/**
 * @author Victor Krieg
 */
public class BuildFileInput extends AbstractDescribableImpl<BuildFileInput>
{
    private final Long projectObjectId;
    private final String scmRevision;
    private final Long fromPackageObjectId;

    @DataBoundConstructor
    public BuildFileInput(Long projectObjectId, String scmRevision, Long fromPackageObjectId)
    {
        this.projectObjectId = projectObjectId;
        this.scmRevision = scmRevision;
        this.fromPackageObjectId = fromPackageObjectId;
    }

    public Long getProjectObjectId()
    {
        return projectObjectId;
    }

    public String getScmRevision()
    {
        return scmRevision;
    }

    public Long getFromPackageObjectId()
    {
        return fromPackageObjectId;
    }

    @Extension
    public static class BuildFileInputDescriptor extends Descriptor<BuildFileInput>
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
