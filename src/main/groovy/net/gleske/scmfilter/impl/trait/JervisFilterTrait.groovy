/*
    Copyright 2014-2020 Sam Gleske

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
package net.gleske.scmfilter.impl.trait;

import net.gleske.jervis.remotes.GitHubGraphQL
import net.gleske.scmfilter.credential.GraphQLTokenCredential
import static net.gleske.jervis.tools.AutoRelease.getScriptFromTemplate

import edu.umd.cs.findbugs.annotations.CheckForNull
import edu.umd.cs.findbugs.annotations.NonNull
import hudson.Extension
import jenkins.model.Jenkins
import jenkins.scm.api.SCMHead
import jenkins.scm.api.SCMSource
import jenkins.scm.api.mixin.ChangeRequestSCMHead
import jenkins.scm.api.mixin.TagSCMHead
import jenkins.scm.api.trait.SCMHeadPrefilter
import jenkins.scm.api.trait.SCMSourceContext
import jenkins.scm.api.trait.SCMSourceTrait
import jenkins.scm.api.trait.SCMSourceTraitDescriptor
import jenkins.scm.api.trait.SCMSourceTraitDescriptor
import jenkins.scm.impl.trait.Selection
import org.apache.commons.lang.StringUtils
import org.jenkinsci.Symbol
import org.jenkinsci.plugins.github_branch_source.GitHubSCMSource
import org.kohsuke.stapler.DataBoundConstructor

import groovy.text.SimpleTemplateEngine
import java.util.logging.Level
import java.util.logging.Logger
import java.util.regex.Pattern
import org.yaml.snakeyaml.Yaml


public class JervisFilterTrait extends SCMSourceTrait {

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
      Returns the YAML file name to search for filters.

      @return a yaml file name
      */
    String getYamlFileName() {
        yamlFileName
    }

    @DataBoundConstructor
    JervisFilterTrait(@CheckForNull String yamlFileName) {
        this.yamlFileName = StringUtils.defaultIfBlank(yamlFileName, DEFAULT_YAML_FILE);
    }

    private static shouldExclude(def filters_obj, String target_ref) {
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
            LOGGER.fine("Malformed filter found on git reference ${target_ref} so we will allow by default")
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
                        LOGGER.fine("Scanning pull request ${head.name}.")
                    }
                    else if(head instanceof TagSCMHead) {
                        // tag
                        binding['git_ref'] = "refs/tags/${head.name}"
                        target_ref = head.name
                        LOGGER.fine("Scanning tag ${head.name}.")
                    }
                    else {
                        // branch
                        binding['git_ref'] = "refs/heads/${head.name}"
                        target_ref = head.name
                        LOGGER.fine("Scanning branch ${head.name}.")
                    }

                    String graphql_query = getScriptFromTemplate(graphql_expr_template, binding)
                    LOGGER.finer("GraphQL query for target ref ${target_ref}:\n${graphql_query}")
                    Map response = github.sendGQL(graphql_query)
                    String yamlText = ''
                    // try to get all requested yaml files from the comma
                    // separated paths provided by the admin configuration
                    String yamlFile = ''
                    response?.get('data')?.get('repository')?.with { Map repoData ->
                        for(int i = 0; i < yamlFiles.size(); i++) {
                            yamlText = (repoData?.get("jervisYaml${i}".toString())?.get('text')?.trim())
                            if(yamlText) {
                                yamlFile = yamlFiles[i]
                                break
                            }
                        }
                    }

                    if(!yamlText) {
                        // could not find YAML or file was empty so should not build
                        LOGGER.finer("On target ref ${target_ref}, could not find yaml file(s): ${yamlFileName}")
                        return true
                    }
                    LOGGER.fine("On target ref ${target_ref}, found ${yamlFile}:\n${['='*80, yamlText, '='*80].join('\n')}\nEND YAML FILE")

                    // parse the YAML for filtering
                    Map jervis_yaml = (new Yaml()).load(yamlText)
                    if(head in TagSCMHead) {
                        // tag
                        if(!('tags' in jervis_yaml)) {
                            // allow all by default
                            return false
                        }
                        return shouldExclude(jervis_yaml['tags'], target_ref)
                    }
                    else {
                        // branch or pull request
                        if(!('branches' in jervis_yaml)) {
                            // allow all by default
                            return false
                        }
                        return shouldExclude(jervis_yaml['branches'], target_ref)
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
