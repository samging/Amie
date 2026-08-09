#!/bin/bash

cd ./packageRepository/ && graldew bootRun

cd .. 
cd ./app/ && kobweb run
