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

import static net.gleske.jervis.remotes.SimpleRestService.objToJson
import static net.gleske.jervis.tools.AutoRelease.getScriptFromTemplate
import static net.gleske.jervis.tools.SecurityIO.sha256Sum
import net.gleske.jervis.remotes.GitHubGraphQL
import net.gleske.jervis.tools.YamlOperator
import net.gleske.scmfilter.credential.GraphQLTokenCredential

import edu.umd.cs.findbugs.annotations.CheckForNull
import edu.umd.cs.findbugs.annotations.NonNull
import hudson.Extension
import jenkins.model.Jenkins
import jenkins.scm.api.mixin.ChangeRequestSCMHead
import jenkins.scm.api.mixin.TagSCMHead
import jenkins.scm.api.SCMHead
import jenkins.scm.api.SCMSource
import jenkins.scm.api.trait.SCMHeadPrefilter
import jenkins.scm.api.trait.SCMSourceContext
import jenkins.scm.api.trait.SCMSourceTrait
import jenkins.scm.api.trait.SCMSourceTraitDescriptor
import jenkins.scm.impl.trait.Selection
import org.apache.commons.lang.StringUtils
import org.jenkinsci.plugins.github_branch_source.GitHubSCMSource
import org.jenkinsci.Symbol
import org.kohsuke.stapler.DataBoundConstructor

import java.time.Instant
import java.util.concurrent.ThreadLocalRandom
import java.util.logging.Logger
import java.util.regex.Pattern

/**
  This trait is responsible for determining if a branch, PR, or Tag is
  buildable in a MultiBranch pipeline.

  This class has tunable properties for making network requests to GitHub.  It
  will introduce a random delay (min 1000ms to max 3000ms) between retrying.
  By default it will retry GitHub API requests up to 60 times.  Time-wise
  retrying can take anywhere from 1-3 minutes based on the randomness before
  this class gives up raising an exception.

  To change minimum range of random delay, start Jenkins with the following
  property.  Value is an Integer (milliseconds).

    -Dnet.gleske.scmfilter.impl.trait.JervisFilterTrait.minRetryInterval=1000

  To change maximum range of random delay, start Jenkins with the following
  property.  Value is an Integer (milliseconds).

    -Dnet.gleske.scmfilter.impl.trait.JervisFilterTrait.maxRetryInterval=3000

  To change the amount of times retry is attempted, start Jenkins with the
  following property.  Value is an Integer (count of retrying).

    -Dnet.gleske.scmfilter.impl.trait.JervisFilterTrait.retryLimit=60
  */
public class JervisFilterTrait extends SCMSourceTrait {

    private static final long serialVersionUID = 1L
    // logger
    private transient static final Logger LOGGER = Logger.getLogger(JervisFilterTrait.name)
    private static final DEFAULT_YAML_FILE = '.jervis.yml'
    private static final graphql_expr_template = '''
        |query {
        |    repository(owner: "${owner}", name: "${repository}") {
        |        <% yamlFiles.eachWithIndex { yamlFileName, index -> %>jervisYaml${index}:object(expression: "${git_ref}:${yamlFileName}") {
        |            ...file
        |        }
        |<% } %>
        |    }
        |}
        |fragment file on GitObject {
        |    ... on Blob {
        |        text
        |    }
        |}
        '''.stripMargin().trim()

    @NonNull
    private final String yamlFileName

    /**
      Get .jervis.yml file from GitHub retrying with a random delay if GitHub
      request fails.

      @param options Instead of parameters you pass parameters by name. [client, query, yamlFiles, log_trace_id]
      @return A response from GitHub with .jervis.yml with keys [yamlFile, yamlText].  Key-values can be Strings, empty string or null.
      */
    private Map getYamlWithRetry(Map options) throws Exception {
        // method options
        GitHubGraphQL github = options.client
        String query = options.query
        List yamlFiles = options.yamlFiles
        String log_trace_id = options.log_trace_id

        // system properties
        Integer minInterval = Integer.getInteger(JervisFilterTrait.name + ".minRetryInterval", 1000)
        Integer maxInterval = Integer.getInteger(JervisFilterTrait.name + ".maxRetryInterval", 3000) + 1
        Integer retryLimit = Integer.getInteger(JervisFilterTrait.name + ".retryLimit", 30)

        // internal values
        List errors = []
        String yamlText = ''
        String yamlFile = ''
        Integer retryCount = 0
        Map response = [:]
        while({->
            if(retryCount > 0) {
                LOGGER.finer("(trace-${log_trace_id}) Retrying GraphQL after failure (retryCount ${retryCount})")
            }
            if(retryCount > retryLimit && errors) {
                LOGGER.finer("(trace-${log_trace_id}) Retry limit reached with GQL errors ${objToJson(errors: errors)}")
                throw new Exception("(trace-${log_trace_id}) Retry limit reached with GQL errors ${objToJson(errors: errors)}")
            } else {
                errors = []
            }
            try {
                response = github.sendGQL(query)
            } catch(Exception httpError) {
                if(retryCount > retryLimit) {
                    LOGGER.finer("(trace-${log_trace_id}) GraphQL HTTP Error: ${httpError.getMessage()}")
                    throw httpError
                }
                // random delay for sleep
                sleep(ThreadLocalRandom.current().nextLong(minInterval, maxInterval))
                retryCount++
                // retry while loop
                return true
            }

            // look for GQL errors
            if('errors' in response.keySet()) {
                errors = response.errors
                // random delay for sleep
                sleep(ThreadLocalRandom.current().nextLong(minInterval, maxInterval))
                retryCount++
                // retry while loop
                return true
            }

            // get data from response
            response?.data?.repository?.with { Map repoData ->
                yamlFiles.eachWithIndex { yamlFileEntry, i ->
                    if(yamlText) {
                        // exit eachWithIndex loop
                        return
                    }
                    yamlText = (repoData?.get("jervisYaml${i}".toString())?.text?.trim())
                    if(yamlText) {
                        yamlFile = yamlFiles[i]
                    }
                }
            }
            // exit the do-while loop
            return false
        }()) continue

        // return Map
        [yamlFile: yamlFile, yamlText: yamlText]
    }

    /**
      Returns the YAML file name to search for filters.

      @return a yaml file name
      */
    String getYamlFileName() {
        yamlFileName
    }

    @DataBoundConstructor
    JervisFilterTrait(@CheckForNull String yamlFileName) {
        this.yamlFileName = StringUtils.defaultIfBlank(yamlFileName, DEFAULT_YAML_FILE)
    }

    protected static shouldExclude(def filters_obj, String target_ref, String log_trace_id) {
        List filters = []
        String filter_type = 'only'
        if(filters_obj instanceof List) {
            filters = filters_obj
        }
        if(filters_obj instanceof Map) {
            if(filters_obj?.get('only') instanceof List) {
                filters = filters_obj?.get('only')*.toString().findAll { it as Boolean }
            }
            else if(filters_obj?.get('except') instanceof List) {
                filters = filters_obj?.get('except')*.toString().findAll { it as Boolean }
                filter_type = 'except'
            }
        }
        if(!filters) {
            LOGGER.fine("(trace-${log_trace_id}) Malformed filter found on git reference ${target_ref} so we will allow by default")
            return false
        }
        String regex = filters.collect {
            if(it[0] == '/' && it[-1] == '/') {
                it[1..-2]
            }
            else {
                Pattern.quote(it)
            }
        }.join('|')
        Boolean matches = Pattern.compile(regex).matcher(target_ref).matches()
        return (filter_type == 'only')? !matches : matches
    }

    @Override
    protected void decorateContext(SCMSourceContext<?, ?> context) {
        context.withPrefilter(new SCMHeadPrefilter() {
                @Override
                public boolean isExcluded(@NonNull SCMSource source, @NonNull SCMHead head) {
                    if(!(source instanceof GitHubSCMSource)) {
                        // wrong type of SCM source so skipping without excluding
                        return false
                    }
                    // ISO-8601 instant timestamp which provides uniqueness
                    // http://www.iso.org/iso/home/standards/iso8601.htm
                    String log_trace_timestamp = Instant.now().toString()
                    String trace_target = (head in ChangeRequestSCMHead) ? head.target.name : head.name
                    // Unique trace ID so that associated log messages can be
                    // followed in Jenkins debug logs
                    String log_trace_id = sha256Sum(log_trace_timestamp + source.repoOwner + source.repository + trace_target)
                    def github = new GitHubGraphQL()
                    // set credentials for GraphQL API interaction
                    github.credential = new GraphQLTokenCredential(source.owner, source.credentialsId)
                    // get GitHub GraphQL API endpoint
                    github.gh_api = ((source.apiUri ?: source.GITHUB_URL) -~ '(/v3)?/?$') + '/graphql'
                    List yamlFiles = []
                    if(yamlFileName.contains(',')) {
                        yamlFiles = yamlFileName.tokenize(',')*.trim()
                    }
                    else {
                        yamlFiles << yamlFileName.trim()
                    }

                    Map binding = [
                        owner: source.repoOwner,
                        repository: source.repository,
                        yamlFiles: yamlFiles
                    ]
                    String target_ref = ''
                    if(head instanceof ChangeRequestSCMHead) {
                        // pull request
                        binding['git_ref'] = "refs/pull/${head.id}/head"
                        target_ref = head.target.name
                        LOGGER.fine("(trace-${log_trace_id}) Scanning pull request ${head.name}.")
                    }
                    else if(head instanceof TagSCMHead) {
                        // tag
                        binding['git_ref'] = "refs/tags/${head.name}"
                        target_ref = head.name
                        LOGGER.fine("(trace-${log_trace_id}) Scanning tag ${head.name}.")
                    }
                    else {
                        // branch
                        binding['git_ref'] = "refs/heads/${head.name}"
                        target_ref = head.name
                        LOGGER.fine("(trace-${log_trace_id}) Scanning branch ${head.name}.")
                    }

                    String graphql_query = getScriptFromTemplate(graphql_expr_template, binding)
                    LOGGER.finer("(trace-${log_trace_id}) GraphQL query for target ref ${target_ref}:\n${graphql_query}")
                    // try to get all requested yaml files from the comma
                    // separated paths provided by the admin configuration
                    Map response = getYamlWithRetry(client: github, query: graphql_query, yamlFiles: yamlFiles, log_trace_id: log_trace_id)
                    String yamlFile = response.yamlFile ?: ''
                    String yamlText = response.yamlText ?: ''

                    if(!yamlText) {
                        // could not find YAML or file was empty so should not build
                        LOGGER.finer("(trace-${log_trace_id}) On target ref ${target_ref}, could not find yaml file(s): ${yamlFileName}")
                        return true
                    }
                    LOGGER.fine("(trace-${log_trace_id}) On target ref ${target_ref}, found ${yamlFile}:\n${['='*80, yamlText, '='*80].join('\n')}\nEND YAML FILE")

                    // parse the YAML for filtering
                    Map jervis_yaml = YamlOperator.loadYamlFrom(yamlText)
                    if(head in TagSCMHead) {
                        // tag
                        if(!('tags' in jervis_yaml)) {
                            // allow all by default
                            return false
                        }
                        return shouldExclude(jervis_yaml['tags'], target_ref, log_trace_id)
                    }
                    else {
                        // branch or pull request
                        if(!('branches' in jervis_yaml)) {
                            // allow all by default
                            return false
                        }
                        return shouldExclude(jervis_yaml['branches'], target_ref, log_trace_id)
                    }
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
