# Upgrade to a major jackrabbit version

1. Create a new worktree based on the new jackrabbit tag that you will use as a baseline (eg: 2.21.19)
  eg: git worktree add -b hippo/jackrabbit-2.21.19-h1 ../jackrabbit-2.21.19-h1 jackrabbit-2.21.19
2. Compare the jackrabbit source code between the previous baseline and new baseline (e.g. 2.21.6 vs 2.21.19)
   1. First eliminate disabled modules from jackrabbit (our hippo-jackrabbit implementation does exclude some jackrabbit modules)
   2. Review all changed files to see if anything relevant to us has changed
3. Set hippo-jackrabbit version to snapshot version on /hippo-jackrabbit/jackrabbit-parent (eg: 2.21.19 to 2.21.19-h1-SNAPSHOT)
  eg: mvn versions:set -DnewVersion=2.21.19-h1-SNAPSHOT -DgenerateBackupPoms=false
4. Commit the version update
  eg: CMS-XXX start hippo/jackrabbit-2.21.19-h1 development branch
5. Open git history of most recent hippo/jackrabbit-XXX branch (eg: hippo/jackrabbit-2.21.6-h1)
   1. Our custom patches start with the commit 'Exclude all unsupport jackrabbit modules'
6. Reapply all changes one-by-one, resolving conflicts as you go
   1. There are about 40+ changes in total -- just do the grunt work

# Release a new hippo-jackrabbit version
1. First, make sure that the initial version is a SNAPSHOT version (eg: 2.21.19-h1-SNAPSHOT)
2. Apply your code changes
3. Set the release version on /hippo-jackrabbit/jackrabbit-parent (eg: 2.21.19-h1-SNAPSHOT to 2.21.19-h1)
  eg: mvn versions:set -DnewVersion=2.21.19-h1 -DgenerateBackupPoms=false
4. Commit the version update
  eg: CMS-XXX Prepare release 2.21.19-h1
5. Create the new hippo-jackrabbit tag
  eg: git tag jackrabbit-2.21.19-h1
6. Push the created tag
  eg: git push origin jackrabbit-2.21.19-h1
7. Deploy to the community and enterprise nexus repository
  eg: mvn clean deploy -Pcommunity-repository && mvn clean deploy -Penterprise-repository
8. Set the next snapshot version on /hippo-jackrabbit/jackrabbit-parent (eg: 2.21.19-h1 to 2.21.19-h2-SNAPSHOT)
  eg: mvn versions:set -DnewVersion=2.21.19-h2-SNAPSHOT -DgenerateBackupPoms=false
9. Commit the version update
  CMS-XXX Prepare release 2.21.19-h2 development
