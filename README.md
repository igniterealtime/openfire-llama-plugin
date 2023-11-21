## LLaMA
AI Inference engine for Openfire using LLaMA.
This plugin is a wrapper to llama.cpp server binary. It uses the HTTP API to create a chatbot in Openfire which will engage in XMPP chat and groupchat conversations.

## CI Build Status

[![Build Status](https://github.com/igniterealtime/openfire-llama-plugin/workflows/Java%20CI/badge.svg)](https://github.com/igniterealtime/openfire-llama-plugin/actions)

## Overview
<img src="https://igniterealtime.github.io/openfire-llama-plugin/llama-chat.png" />

## Known Issues

This version has embedded binaries for only Linux 64 and Windows 64.

## Installation

copy llama.jar to the plugins folder

## Configuration
<img src="https://igniterealtime.github.io/openfire-llama-plugin/llama-settings.png" />

### Enable LLaMA
Enables or disables the plugin. Reload plugin or restart Openfire if this or any of the settings other settings are changed.

### Username/Password
This is Openfire username/password for the user that will act as a chatbot for LLaMA. By default the user will be “llama” and the password witll be a random string. If you are using ldap or your Openfire user manager is in read-only mode and a new user cannot be created, then you must create the user and specify the username and password here…

### Alias
Set an alias for the model. The alias will be returned in chat responses.

### Port
Set the port to listen. Default: 8080

### Model URL
Specify the path to the LLaMA model file to be downloaded and used. Default is https://huggingface.co/TheBloke/Llama-2-7B-Chat-GGUF/resolve/main/llama-2-7b-chat.Q2_K.gguf?download=true
The first time the plugin starts, it will download this file and cache in OPENFIRE_HOME/llama folder.

### System Prompt
Prompting large language models like Llama 2 is an art and a science. Set your system prompt here. Default is "You are a helpful assistant"

### Predictions
Set the maximum number of tokens to predict when generating text. Note: May exceed the set limit slightly if the last token is a partial multibyte character. When 0, no tokens will be generated but the prompt is evaluated into the cache. Default is 256

### Temperature
Adjust the randomness of the generated text (default: 0.5).

### Top K Sampling
Limit the next token selection to the K most probable tokens (default: 40).

### Top P Sampling
Limit the next token selection to a subset of tokens with a cumulative probability above a threshold P (default: 0.95).

## How to use
<img src="https://igniterealtime.github.io/openfire-llama-plugin/llama-test.png" />
To confirm that llama.cpp is working, use the demo web app to test.
The plugin will create an Openfire users called llama (by default). The user can be engaged with in chat or groupchats from any XMPP client application like Spark, Converse or Conversations.


### Chat
Add llama as a contact and start a chat conversation

### Groupchat
Start as chat message with the LLaMA username (llama) and LLaMA will join the groupchat and respond to the message typed. Not that this only works with groupchats hosted in your Openfire server. Federation is not supported.
