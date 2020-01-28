

- init needs to use yaml files from resources because we need comments. tests should ensure they parse ok

- rollover report
- email sending
- itac overlay
- commit overlay to proposals
- queue summary (partner charges, band cutoffs) as text hopefully?
- skeleton creation

It would be nice if output could just tell you quickly whether or not the generated queue is ok, without having to write out a bunch of files. Maybe we can have a `--save` option that you can add (maybe with optional `--name`) which will create the output dir for you.

It would be nice to figure out how to use the various variants. Can we do `itac switch <variant>` to swap out all the config files and stash the current ones in a `stash-yyyymmdd-hhmmss` directory?

Could also say `itac stash` (`--name`) at any point I guess, to create a stash directory with no queue.

___

should we compute a hash of the proposals to go along with a generated queue so we don't accidentally generate skeletons using a queue generated from a differemt set of proposals? same for sending emails?

nice to have

- create new default config files after `init`
  `itac config -common` or `-gn` or `-gs` with optional `-o blah.yaml`




