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
1. mvn versions:set -DnewVersion=2.20.X-hX -DgenerateBackupPoms=false
1. mvn clean deploy
1. git tag jackrabbit-2.20.X-h1
1. git push origin jackrabbit-2.20.X-hX
