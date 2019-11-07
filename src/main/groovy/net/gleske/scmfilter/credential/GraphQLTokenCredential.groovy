package net.gleske.scmfilter.credential

import net.gleske.jervis.remotes.creds.ReadonlyTokenCredential

import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials
import jenkins.model.Jenkins
import hudson.model.Item

class GraphQLTokenCredential implements ReadonlyTokenCredential {
    public final Item owner
    public final String credentialsId
    GraphQLTokenCredential(Item owner, String credentialsId) {
        this.owner = owner
        this.credentialsId = credentialsId
    }
    String getToken() {
        CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials, owner, Jenkins.instance.ACL.SYSTEM).find {
            it.id == credentialsId
        }.with {
            return (it?.password?.plainText) ?: ''
        }
    }
}
