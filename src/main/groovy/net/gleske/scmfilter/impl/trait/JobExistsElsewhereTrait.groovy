/*
    Copyright 2014-2023 Sam Gleske

    Permission is hereby granted, free of charge, to any person obtaining a
    copy of this software and associated documentation files (the "Software"),
    to deal in the Software without restriction, including without limitation
    the rights to use, copy, modify, merge, publish, distribute, sublicense,
    and/or sell copies of the Software, and to permit persons to whom the
    Software is furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in
    all copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
    FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
    DEALINGS IN THE SOFTWARE.
 */
package net.gleske.scmfilter.impl.trait

// static imports
import static net.gleske.jervis.tools.SecurityIO.sha256Sum

// Jenkins imports
import edu.umd.cs.findbugs.annotations.CheckForNull
import edu.umd.cs.findbugs.annotations.NonNull
import hudson.Extension
import jenkins.model.Jenkins
import jenkins.plugins.git.AbstractGitSCMSource
import jenkins.scm.api.mixin.ChangeRequestSCMHead
import jenkins.scm.api.SCMHead
import jenkins.scm.api.SCMSource
import jenkins.scm.api.trait.SCMHeadPrefilter
import jenkins.scm.api.trait.SCMSourceContext
import jenkins.scm.api.trait.SCMSourceTrait
import jenkins.scm.api.trait.SCMSourceTraitDescriptor
import jenkins.scm.impl.trait.Selection
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject
import org.jenkinsci.Symbol
import org.kohsuke.stapler.DataBoundConstructor

// Java imports
import java.time.Instant
import java.util.concurrent.ThreadLocalRandom
import java.util.logging.Logger

/**
  This trait will find similarly configured jobs elsewhere in the Jenkins
  instance and only if the other companion jobs exist will it allow the current
  job to be created.

  Intended for multibranch pipelines; if another multibranch pipeline exists
  with the same repository and has a companion ref job (PR, branch, or Tag),
  then it will proceed creating the same job in the current multibranch
  pipeline.  This search excludes the current job where this trait is
  configured.
  */
public class JobExistsElsewhereTrait extends SCMSourceTrait {
    // serialization version
    private static final long serialVersionUID = 1L
    @NonNull
    private final String includePrefix = ''
    @NonNull
    private final String excludePrefix = ''
    @NonNull
    private final Integer timeToSearch = 60

    private transient static final Logger LOGGER = Logger.getLogger(JobExistsElsewhereTrait.name)

    /**
      @param includePrefix A string which the full path of the job must match
                           at the start to be considered for job creation.
                           Alternately, you can use a Pattern if the value
                           starts and ends with a forward slash.
      @param excludePrefix A string which the full path of the job must NOT
                           match at the start to be considered for job
                           creation.  Alternately, you can use a Pattern if the
                           value starts and ends with a forward slash.
      @param timeToSearch  The amount of time (in seconds) this filter should
                           wait and continue to search anticipating a job to
                           eventually exist.  If the job existing elsewhere
                           gets created before timeToSearch is reached, then
                           this will allow a companion job to be created.  If
                           the timeToSearch limit is reached it will give up
                           looking and not create this companion job.
      */
    @DataBoundConstructor
    JobExistsElsewhereTrait(@CheckForNull String includePrefix, @CheckForNull String excludePrefix, @CheckForNull Integer timeToSearch) {
        this.includePrefix = includePrefix?.trim()
        this.excludePrefix = excludePrefix?.trim()
        this.timeToSearch = timeToSearch
    }

    /**
      Get the string to be used to match jobs when searching for another job to exist.
      */
    String getIncludePrefix() {
        this.includePrefix
    }

    /**
      Get the string to be used to exclude jobs when searching for another job to exist.
      */
    String getExcludePrefix() {
        this.excludePrefix
    }

    /**
      Return the maximum amount of seconds this job should continue to retry
      checking if another job exists within Jenkins.
      */
    Integer getTimeToSearch() {
        this.timeToSearch
    }

    private Boolean isIncluded(String filter, String fullName, String log_trace_id) {
        if(!filter) {
            return true
        }
        if(!(filter.startsWith('/') && filter.endsWith('/'))) {
            return fullName.startsWith(filter)
        }
        // pattern search; in this case "except" returns true if there's a match
        JervisFilterTrait.shouldExclude([except: [filter]], fullName, log_trace_id)
    }

    @Override
    protected void decorateContext(SCMSourceContext<?, ?> context) {
        context.withPrefilter(new SCMHeadPrefilter() {
                @Override
                public boolean isExcluded(@NonNull SCMSource source, @NonNull SCMHead head) {
                    if(!(source instanceof AbstractGitSCMSource) || !source?.getRemote()?.trim()) {
                        // wrong type of SCM source so skipping and excluding
                        return true
                    }
                    String log_trace_timestamp = Instant.now().toString()
                    String trace_target = head.name
                    String remote = source.getRemote()
                    String log_trace_id = sha256Sum(log_trace_timestamp + remote + trace_target)
                    List matchedParentJobs = Jenkins.instance.getAllItems(WorkflowMultiBranchProject).findAll { job ->
                        Boolean includeJob = isIncluded(getIncludePrefix(), job.fullName, log_trace_id)
                        if(includeJob && getExcludePrefix()) {
                            includeJob = !isIncluded(getExcludePrefix(), job.fullName, log_trace_id)
                        }
                        Boolean remoteMatches = job?.sources?.any {
                            (it?.source instanceof AbstractGitSCMSource) &&
                            it?.source?.getRemote() == remote
                        } ?: false

                        // should the job be matched?
                        includeJob && remoteMatches
                    }
                    if(!matchedParentJobs) {
                        LOGGER.finer("(trace-${log_trace_id}) no matching multibranch pipelines found.")
                        // no parent jobs found so it is excluded
                        return true
                    }
                    Boolean existsElsewhere = matchedParentJobs.any { it.getItem(trace_target)?.isDisabled() }
                    if(existsElsewhere) {
                        // job found so create a companion pipeline job
                        return false
                    }
                    if(!getTimeToSearch()) {
                        // exclude because no timeToSearch limit has been set
                        return true
                    }
                    // start searching up to a limit of timeToSearch
                    Long timeLimit = Instant.now().epochSecond + getTimeToSearch().toLong()
                    while(Instant.now().epochSecond < timeLimit) {
                        sleep(ThreadLocalRandom.current().nextLong(1000, 3001))
                        existsElsewhere = matchedParentJobs.any { it.getItem(trace_target)?.isDisabled() }
                        if(existsElsewhere) {
                            // job found so create a companion pipeline job
                            return false
                        }
                    }
                    // exclude because time limit reached
                    return true
                }
            })
    }

    @Symbol("jobExistsElsewhereFilter")
    @Extension
    @Selection
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.JobExistsElsewhereTrait_DisplayName()
        }
    }
}
