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
package net.gleske.scmfilter.credential

import net.gleske.jervis.remotes.creds.ReadonlyTokenCredential

import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials
import jenkins.model.Jenkins
import hudson.model.Item

class GraphQLTokenCredential implements ReadonlyTokenCredential {
    private static final long serialVersionUID = 1L
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
