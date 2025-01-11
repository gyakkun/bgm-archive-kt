# bgm-archive-kt

A backend service for handling html and json files crawled from bgm-archive-sh.

## Features

It's a monolithic app that consists of the following features:

* Process the html files crawled by [bgm-archive-sh](https://github.com/gyakkun/bgm-archive-sh). HTML file will be
  converted to json file with the stem contents. The json file will be lie on another git repository resembles the
  folder structure of html repo. The process is commit by commit, which means each commit in html repo will be reflected
  in a commit in the json repo. These linked commit share the same commit message. Check `/hook/commit` for details.
* Persist the json file to database. Each topic is now converted to a json file. To efficiently query the information we
  need, the app will pour the db with fields from json file. Note that the `contentHtml` will not be put into db. Other
  information like topic title, topic id, post id, likes and also blog tags will be inserted to db. Check `/hook/db` for
  details.
* Expose the history of each topic via restful api. Now that we have version management for each topic on top of git,
  it's easy to expose them via api. Check the contents under `/history` path for details.
* Build git commit history cache in db. The query performance of git history isn't great, so some cache is built in db.
  Check the `ba_cache_*` tables and definitions and `/hook/cache` for details.
* Forum enhance handler. To enhance the forum experience, some endpoints are built to query the statistic data of users
  for specific type of topics. Also, to leverage the snapshots managed by git, an endpoint to query deleted post is
  built. Check the contents under `/forum-enhance` path for details.
* Holes detection. It's possible that the `bgm-archive-sh` not able to find some hidden topics from homepage. So some
  endpoints to detect and to mask the holes for deleted/uncrawled topics are built. Check `/holes` for details.
* Spot-check. Some topics could be changed and not popped up in homepage. Spot checker will randomly pick topic ids
  during processing html commits and write to the `sc.txt` file under topic folder of html repos. `bgm-archive-sh` will
  check this file in the next round and will crawl those topic ids in the `sc.txt` file.

## Usage

Check the config file example: [config.sample.json](src/main/resources/config.sample.json).

Most default settings are good enough. By default the working directory will be `~/source`.

You should provide the `repoList`, otherwise nothing will be processed and persisted.

## Tips

* For bare repo
  Please run: 
  ```bash
  $ git config remote.origin.fetch 'refs/heads/*:refs/heads/*'
  ```
  to make fetch same as pull