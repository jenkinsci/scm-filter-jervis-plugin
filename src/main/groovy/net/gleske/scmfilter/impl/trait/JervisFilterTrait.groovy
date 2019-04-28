package net.gleske.scmfilter.impl.trait;

import hudson.Extension
import jenkins.scm.api.trait.SCMSourceContext
import jenkins.scm.api.trait.SCMSourceTrait
import jenkins.scm.api.trait.SCMSourceTraitDescriptor
import jenkins.scm.api.trait.SCMSourceTraitDescriptor
import jenkins.scm.impl.trait.Selection
import org.jenkinsci.Symbol
import org.kohsuke.stapler.DataBoundConstructor
import jenkins.scm.api.trait.SCMHeadPrefilter
import edu.umd.cs.findbugs.annotations.NonNull
import jenkins.scm.api.SCMSource
import jenkins.scm.api.SCMHead
import jenkins.scm.api.mixin.ChangeRequestSCMHead
import jenkins.scm.api.mixin.TagSCMHead


public class JervisFilterTrait extends SCMSourceTrait {
    @DataBoundConstructor
    JervisFilterTrait() {}

    @Override
    protected void decorateContext(SCMSourceContext<?, ?> context) {
        context.withPrefilter(new SCMHeadPrefilter() {
                @Override
                public boolean isExcluded(@NonNull SCMSource source, @NonNull SCMHead head) {
                    // always include (for now until we get a real filter)
                    false
                }
            })
    }

    @Symbol("jervisFilter")
    @Extension
    @Selection
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.JervisFilterTrait_DisplayName()
        }
    }
}
