package net.gleske.scmfilter.credential

import net.gleske.jervis.remotes.creds.ReadonlyTokenCredential

import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials
import jenkins.model.Jenkins
import hudson.model.Item

class GraphQLTokenCredential implements ReadonlyTokenCredential {
    Item owner
    GraphQLTokenCredential(Item owner) {
        this.owner = owner
    }
    String getToken() {
        CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials, this.owner, Jenkins.instance.ACL.SYSTEM).find {
            it.id == source.credentialsId
        }.with {
            return (it?.password?.plainText) ?: ''
        }
    }
}
