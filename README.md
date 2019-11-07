# SCM Filter Jervis YAML Plugin

This plugin is intended for Jenkins infrastructure relying on [jervis][jervis]
to deliver software in a self-service manner.

This plugin can also be used for Travis CI YAML.

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
    - master
```

# More on specify branches and tags to build

By default Jervis will generate Jenkins jobs for all branches that have a
`.jervis.yml` file.  You can control and limit this behavior by specifying the
`branches` or `tags` key in your `.jervis.yml`.

### Whitelist or blacklist branches and tags

You can either whitelist or blacklist branches that you want to be built:

```yaml
# blacklist
branches:
  except:
    - legacy
    - experimental

# whitelist
branches:
  only:
    - master
    - stable
```

The same YAML can be applied to tags.

```yaml
# blacklist
tags:
  except:
    - /.*-rc/
    - /.*-beta/

# whitelist
tags:
  only:
    - /v[.0-9]+/
```

If you specify both `only` and `except`, then `except` will be ignored.
`.jervis.yml` needs to be present on all branches you want to be built.
`.jervis.yml` will be interpreted in the context of that branch so if you
specify a whitelist in your master branch it will not propagate to other
branches.

### Using regular expressions

You can use regular expressions to whitelist or blacklist branches:

```yaml
branches:
  only:
    - master
    - /^[.0-9]+-hotfix$/
```

Any name surrounded with `/` in the list of branches is treated as a regular
expression.  The expression will use [`Pattern.compile`][java-pattern] to
compile the regex string into a [Groovy regular expression][groovy-regex].

[groovy-regex]: http://docs.groovy-lang.org/latest/html/documentation/index.html#_regular_expression_operators
[java-pattern]: https://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html#compile%28java.lang.String%29
[jervis]: https://github.com/samrocketman/jervis/wiki
