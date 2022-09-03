# CQRS Demo App

A fullstack CQRS app inspired by https://www.youtube.com/watch?v=qDNPQo9UmJA


# TODO
- [x] set up kafka stream
  - [x] docker
  - [x] create 3 topics
    - pending
    - accepted
    - failed
- [x] find a kafka read/write lib
- [ ] build web services logic
  - [x] install ring and liberator
  - [ ] create `commands` enpoint
    - [x] accept POST
    - [x] put on queue
  - [ ] create `updates` endpoint
    - [ ] websockets
    - [ ] read `pending` and echo

  - [ ] create user logic
    - generate UUID
    - email
- [ ] set up web services container 
- [ ] set up consumer services
  - read from pending
  - transact to DB
  - update `accepted`

Later:
- [ ] add spec to validate input data
- [ ] sync version of commands post
