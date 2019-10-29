# ITAC CLI

This is a command-line tool that provides a bare-bones way to do ITAC stuff. The intent is that it will replace the existing very horrible web-based software.

The `engine` module contains sources taken from the old `itac/queue-engine` with modernized dependencies and accompanying code changes. The code is otherwise unchanged for now and it generates a bazillion warnings with our normal compiler flags turned on, so they're turned off.

The `main` module contains the command-line app.

