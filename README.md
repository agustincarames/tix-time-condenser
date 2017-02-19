# tix-time-condenser

This is the `tix-time-condenser` microservice. The idea behind it is to condense all the reports from the server into the directories of each user and installation.  
It also checks the package integrity and coherence by performing a check upon the message, signature and public key provided.

## Installation

The `tix-time-condenser` is currently in CD thourgh Travis and DockerHub. So to install it you just need to download the latest release of this repository container and run the image.

Since this microservice is made using SpringBoot, you can update any configuration variable using environment variables or passing them to the image at runtime.

It is important to add a volume to the container, since the image may be fragile, or there might be more than one image, but the reports must always be kept safe for processing.

## Configurations

The `tix-time-condenser` has four SpringBoot Profiles. `dev` or `default`, `test`, `staging` and `production`. While they are self explanatory, you should bare in mind that

  * `dev` or `default` are for development purposes only
  * `test` is only intended for the CI environment
  * `staging` is to deploy the microservice in an unestable environment
  * `production` is to deploy the microservice in the stable environment
  
Both `staging` and `production` have almost no difference whatsoever in the performance department. The only difference is in the queue name where the new packets are received.
