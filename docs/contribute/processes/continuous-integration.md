---
title: Continuous Integration Process
---

## Push triggered jobs
If a new commit arrives in a local branch, Travis CI triggers a new job which builds and tests the current state. During the
test phase only the JUnit tests (not STF tests) are executed. Furthermore the static code analysis service [Codacy](https://www.codacy.com/) analysis 
the new commit. New issues are reported in the [codacy branch overview](https://app.codacy.com/app/Saros/saros/commits).

## Pull request triggered jobs
If a pull request is created two Travis CI jobs are executed. See the [travis documentation](https://docs.travis-ci.com/user/pull-requests/#%E2%80%98Double-builds%E2%80%99-on-pull-requests) for more information.
These jobs are the same as the jobs triggered by a push. The only difference is that issues of Codacy are reported as comments in the pull request and the [Codacy Pull Request Overview](https://app.codacy.com/app/Saros/saros/pullRequests).

## Daily jobs
Travis CI executes a daily cron job that triggers the following builds:
* Builds and tests the current master branch
  * If this job fails the failure is reported in GitHub
* Builds Saros/E and executes the STF tests
  * If this job fails **the failure is not reported to GitHub**
* Builds Saros/E and executes the STF self tests
  * If this job fails **the failure is not reported to GitHub**
