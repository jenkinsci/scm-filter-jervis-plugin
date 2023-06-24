# SCM Filter Jervis YAML Plugin

This plugin is intended for Jenkins infrastructure relying on [jervis][jervis]
to deliver software in a self-service manner.

This plugin can also be used for Travis CI YAML.

# How to configure this plugin

1. Configure a multibranch pipeline job or a GitHub organization job.
2. Select GitHub Branch Source as the SCM type for the repository.
3. Under behaviors, click the `Add` button and select `Filter by Jervis YAML for
   branches, pull requests, and tags.`

   ![Screenshot of add Jervis behavior][screenshot-add-jervis]

4. Save or customize the YAML file name.  You can customize the YAML file name
   to be something other than `.jervis.yml` at the root of the repository.

   ![Screenshot of configuring Jervis behavior][screenshot-configure-jervis]

[screenshot-add-jervis]: https://user-images.githubusercontent.com/875669/68359670-beb37280-00ea-11ea-8b71-1f19e4d926e9.png
[screenshot-configure-jervis]: https://user-images.githubusercontent.com/875669/68359738-09cd8580-00eb-11ea-9782-c68d218c8b79.png

# Short Introduction

This will look at the root of a GitHub reference for `.jervis.yml` for the
branches and tags filtering.  You can customize the name of the YAML file
searched for if you like.

For Tags:

- It will filter for the tag name.

For Branches:

- It will filter for the branch name.
- It will filter for pull requests destined for the branch name.

### Example YAML

```yaml
branches:
  only:
    - main
```

# More on specify branches and tags to build

By default Jervis will generate Jenkins jobs for all branches that have a
`.jervis.yml` file.  You can control and limit this behavior by specifying the
`branches` or `tags` key in your `.jervis.yml`.

### Allow or block branches and tags

You can either create an allow list of branches (only) or a block list of
branches (except) to be built.

```yaml
# block branches from building
branches:
  except:
    - legacy
    - experimental

# allow only these branches
branches:
  only:
    - main
    - stable
```

The same YAML can be applied to tags.

```yaml
# block tags from building
tags:
  except:
    - /.*-rc/
    - /.*-beta/

# allow only these tags
tags:
  only:
    - /v[.0-9]+/
```

If you specify both `only` and `except`, then `except` will be ignored.
`.jervis.yml` needs to be present on all branches you want to be built.
`.jervis.yml` will be interpreted in the context of that branch so if you
specify an allow list in your main branch, then it will not propagate to other
branches.

### Using regular expressions

You can use regular expressions to allow or block branches:

```yaml
branches:
  only:
    - main
    - /^[.0-9]+-hotfix$/
```

Any name surrounded with `/` in the list of branches is treated as a regular
expression.  The expression will use [`Pattern.compile`][java-pattern] to
compile the regex string into a [Groovy regular expression][groovy-regex].

[groovy-regex]: http://docs.groovy-lang.org/latest/html/documentation/index.html#_regular_expression_operators
[java-pattern]: https://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html#compile%28java.lang.String%29
[jervis]: https://github.com/samrocketman/jervis/wiki
