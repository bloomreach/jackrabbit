# Upgrade major version Jackrabbit

1. Create a new worktree based on the current Jackrabbit tag we will use as a baseline
git worktree add -b hippo/jackrabbit-2.20.X-hX ../jackrabbit-2.20.X-hX jackrabbit-2.20.X
1. Compare the sources between the previous baseline and new baseline (e.g. 2.16.2 vs 2.18.3)
   1. First eliminate disabled modules from jackrabbit
   1. Review all changed files to see if anything relevant to us has changed
1. Open git history of most recent hippo/jackrabbit-XXX branch
   1. Our changes start with REPO-XXXX.
1. Reapply all changes one-by-one, resolving conflicts as you go
   1. There are about 25 changes total -- just do the grunt work.

# Release new Hippo Jackrabbit
1. First, make sure that the initial version is a SNAPSHOT version
1. Apply your changes
1. Update the version to a release version: e.g. `for pom in $(find . -name pom.xml ); do sed -i 's/2.21.6-h3-SNAPSHOT/2.21.6-h3/g' $pom; done`
1. git commit
1. git tag -a
1. Deploy to the enterprise nexus: `mvn deploy`
1. Deploy to the community nexus: `mvn deploy -DaltDeploymentRepository=hippo-maven2::default::https://maven.onehippo.com/content/repositories/releases/`
1. Set the next Snapshot version: e.g. `for pom in $(find . -name pom.xml ); do sed -i 's/2.21.6-h3/2.21.6-h4-SNAPSHOT/g' $pom; done`
1. git commit
1. git push --follow-tags
