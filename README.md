Goo tool
========

This is an implementation of our multi-purpose platform tool. It allows a user to:

  * deploy our apps to EC2 server instances
  * view EC2 server groups and modify their sizes (known as 'scaling up/down a group')
  * issue cloudformation commands
  
There are already ways to do these things; a user can use the Amazon CLI or dashboard,
the riff-raff dashboard, or the existing ruby Gu gem. This implementation aims to be
easier to use, because the frontend architecture means that batches of commands are
usually required to do something, or the configuration of various stages and parameters
can be time-consuming.

Developers can publish new versions of goo using our maven repo, and users receive 
updates the next time they use the tool.

Setup
-----

For existing users of gu, the setup is identical and no further setup is necessary.
Users of this tool need the following:
      
A fog configuration file. Make a file `~/.fog` and ensure it has the contents:
  
    default:
      aws_access_key_id: _YOUR_KEY_
      aws_secret_access_key: _YOUR_SECRET_
      region: 'eu-west-1'  
        
A riff-raff key file. Make a file `~/.riffraff` and ensure it has the contents:

    _YOUR_KEY_ 
    
A riff-raff key can be made [here](https://riffraff.gutools.co.uk/apiKeys/list)   

Commands
--------

All commands can be run with the helper script, called [goo](../goo). For ease of use,
an alias could be created for global availability. The syntax is similar to gu, where
subcommands are used with parameters.

###Groups

These subcommands are for working with Amazon autoscaling groups.

####`groups list`

List all the EC2 autoscaling groups.

`./goo groups list` returns:

    frontend-CODE-AdminAutoscalingGroup-XXXX               1/1/2
    frontend-CODE-ApplicationsAutoscalingGroup-XXXX        1/1/2
    frontend-CODE-ArchiveAutoscalingGroup-XXXX             1/1/2
    ...

####`groups list <grep_pattern>`

List further detail about the matching EC2 autoscaling groups.

`./goo groups list admin` returns:

    frontend-CODE-AdminAutoscalingGroup-XXXX                
      Load Balancers: [frontend-AdminLoa-XXXX]
      Min/Desired/Max = 1/1/2
    
      i-3739ee74 Healthy/Terminating
      i-37ad1875 Healthy/InService
    
    frontend-PROD-AdminAutoscalingGroup-XXXX                
      Load Balancers: [frontend-AdminLoa-XXXX]
      Min/Desired/Max = 1/1/2
    
      i-07a51045 Healthy/InService

`./goo groups list code-admin` returns:

    frontend-CODE-AdminAutoscalingGroup-XXXX                
      Load Balancers: [frontend-AdminLoa-XXXX]
      Min/Desired/Max = 1/1/2
    
      i-3739ee74 Healthy/Terminating
      i-37ad1875 Healthy/InService 

####`groups update <autoscaling_group> <min> <desired> <max>`

Update the named EC2 autoscaling group with the provided instance parameters.
  
`./goo groups update frontend-CODE-AdminAutoscalingGroup-XXXX 1 1 2` returns:

    Updated autoscaling group

###EC2

There is currently only one subcommand for checking EC2 instances.

####`ec2 list`

List all known EC2 instances.

`./goo ec2 list` returns:

<img src="doc/ec2-list.png" alt="Ec2 list" width="600px" height="300px" />

###Deploy

Initiate riff-raff deployments for a given staging environment.

####`deploy list`

Lists all the applications that this tool is configured to deploy. The list contains aliases that can be used in deploy command. 

`./goo deploy list` returns:

    article
    facia
    applications
    sport
    identity
    admin
    facia-tool
    discussion
    onward
    diagnostics
    commercial

####`deploy`

Deploys frontend projects to a given environment. The subcommand uses riff-raff aliases, so for 
instance: `deploy article` will deploy `frontend-article`, and similarly for the other frontend applications.

The `deploy` command takes the following options:

* `--prod`: Deploy to production.
* `--code`: Deploy to code.
* `--name STRING`: Name of the application you want to deploy, or a comma separated list of apps. Default to all apps.

`./goo deploy --code` returns:

    Deploying article - http://riffraff.gutools.co.uk/deployment/view/c7eacaa1-e46b-4b27-9036-ad81f10af020
    Deploying facia - http://riffraff.gutools.co.uk/deployment/view/46b57358-4d6c-41b5-924e-b970eeed1684
    Deploying applications - http://riffraff.gutools.co.uk/deployment/view/392a8091-3bc8-405d-ae08-6fac55fc5925
    Deploying sport - http://riffraff.gutools.co.uk/deployment/view/1ea521a3-8c3a-4ddc-a9bc-420e92396d4e
    Deploying identity - http://riffraff.gutools.co.uk/deployment/view/4a7e4927-d891-4ca1-b3aa-31571f8d0265
    Deploying admin - http://riffraff.gutools.co.uk/deployment/view/8d351d0f-e7af-4605-a01c-cc4c407f5131
    Deploying facia-tool - http://riffraff.gutools.co.uk/deployment/view/68888d5d-b5e7-47ed-90a8-1160577f37a1
    Deploying discussion - http://riffraff.gutools.co.uk/deployment/view/7658b19a-7618-4715-9e46-852a79e51b8a
    Deploying onward - http://riffraff.gutools.co.uk/deployment/view/9cc85a36-7909-4f64-b2c1-9989094f61b9
    Deploying diagnostics - http://riffraff.gutools.co.uk/deployment/view/c5871a05-a205-4eef-9b04-dc6d7ac16d7c
    Deploying commercial - http://riffraff.gutools.co.uk/deployment/view/62c7e4bd-f27d-4874-be7c-4dd9b9f77c3f

`./goo deploy --code --name admin` returns:

    Deploying admin - http://riffraff.gutools.co.uk/deployment/view/6b6592dc-f487-46a1-a340-b61258e980cd

`./goo deploy --code --name article,onward` returns:

    Deploying article - http://riffraff.gutools.co.uk/deployment/view/e58d7c43-96b0-4b7b-b217-81b7ba2aef1f
    Deploying onward - http://riffraff.gutools.co.uk/deployment/view/d2402528-d8dc-404a-bc09-6310b98442cf

###Cloudformation

####`cloudformation up`

Creates a new stack with the given environment.

`./goo cloudformation update --code frontend-logger` returns:

    Uploaded template from: /platform/cloudformation/frontend-logger.json
    Create Stack Request sent successfully.

####`cloudformation update`

Updates an existing cloudformation stack. Do check the [AWS documentation](http://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks.html)
concerning resource updates.

`./goo cloudformation update --code frontend` returns:
    
    Uploaded template from: /platform/cloudformation/frontend.json
    Update Stack Request sent successfully.
    
####`cloudformation destroy`

Destroys an existing stack.

`./goo cloudformation destroy --code frontend-logger` returns:

    Delete Stack Request sent successfully.

For safety reasons, the tool does not permit deletion of PROD stacks. Please do so manually.
    
`./goo cloudformation destroy --prod frontend-logger` returns:

    Can not delete PROD stacks

Development
-----------

The tool is made up of three parts:

1. a bash script [goo](../goo), which executes
2. an sbt project `goo-client`, which is a bootstrap loader for
3. a maven-hosted library `frontend-goo-tool`

This convoluted approach enables users to invoke the bash script simply with parameters, whilst maven's project versioning is given the task of resolving the latest published version of the goo tool.

Developers can run sbt using the supplied [sbt script](sbt). From here, the `dev-build` project can be used for development.

For testing, developers can use the `dev-build` sbt project to invoke tasks in a similar manner to the [goo](../goo) script.
In sbt, `dev-build` can be executed using `run ec2 list`, for example.  
 
Publishing
----------

A new version of `frontend-goo-tool` can be uploaded to the [Guardian Github](https://github.com/guardian/guardian.github.com) releases Maven repository.
This project is configured so developers can run `publish` for a local-file maven publish.

A copy of the locally-published maven project `frontend-goo-tool`is expected to be added to the 
[maven repository](https://github.com/guardian/guardian.github.com/tree/master/maven/repo-releases/com/gu/frontend-goo-tool_2.10).
Remember to do the following:

* Increment the version number in [build.scala](project/build.scala)
* Run the `update-directory-index.sh` indexer in the [Guardian Github repository](https://github.com/guardian/guardian.github.com)




