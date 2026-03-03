# Ring Buffer (Single Writer, Multiple Readers)

## 
A fixed-size ring buffer (capacity **N**) loop with:
- a CLI to declare capacity N as well as visualize the buffer
- one writer
- multiple readers
- each reader has its own cursor
- reads do not delete items for other readers
- when full, the writer overwritesold data (slow readers may miss items)
- readers are in different initial positions (offset) -- can be changed for liking

## Design 
- **RingBuffer<T>**
  - owns the fixed array and capacity
  - writes using a circular `writeIndex`
  - keeps a `writeSeq` counter so readers can detect overwrite
  - creates readers (chooses their start position)
- **Writer**
  - the only component that calls `write()`
  - runs in its own thread and writes increasing numbers
- **Reader<T>**
  - has its own `nextSeqToRead`
  - `read()` returns the next item for that reader
  - if it fell behind (overwritten), it skips forward
- **ReaderStart**
  - `FROM_NOW` or `FROM_OLDEST_AVAILABLE`

## Run
Compile:
```bash
javac -d out ringbuffer/*.java

java -cp out ringbuffer.Main {N}
```
### To exit the ringbuffer loop, use keyboardInterrupt (CTRL + C) 
### ***It is recommended to run the loop for a short amount of time***
### ***~= 4-5 seconds***
### ***to avoid too much noise in the CLI***


## UML Diagrams 


### UML Class Diagram 
![UML Class Diagram ](https://img.plantuml.biz/plantuml/png/XLJBJiCm4BpdA_Pe1Eq7LAWY290gjKHg0nmGGcwy3GRNARR3GmY-7Rz27AZ49P9tTcTdn-kSQ4ELQRSYsz7gYTO8YijwtMusgE0Z0wW4rHeMb4j_13Y4hJqGgcezMHcNVNjtO97Fp7vGjbjO8cMeLeuurbmiYyNzPN7J7uhvsVcglC-lyzayFvsVTmIXtTAoddeDn-KqSeo0ImCLjJgvUO-XOls8bRcz0qODZJ7Ho1fU5JUumkUar8Tcak7ReDZcLqznihuliHK7CPhs9LtmfU4iK9AoYuN1dUHA8JKOpYJnumY2PKTUPVXsrG41XkksTa9Yg_7u0SKEbHwF_VbhEE2Lr7I3CJTYZM2eJVv2kQ1hiIUhX1fDRdgWqv7SmOrhe7xCJwva9WtMgAOGrYIrdQ6WxmkTXggcjVag3XbOoHwseHcgbUINeDxTFPhANsgYcP9kyHzz4j-S4MNZ-6FtOcTu8wbmjFOcsS-j-ufTexd3IReWhfrVBh9t1F-0HgDfeXacSKrqYaVR9-0VvW0A1lIGnMBs7-AmcAvxBFgJuSQ2k5akhSS6hNg5AL6_qDbdTeAIkH_8Dm00)


### Sequence diagram for write
![Sequence diagram for write](https://img.plantuml.biz/plantuml/png/RT0z2y8m40RWFP3_S0X2It5m5HJfrhKg7SJXQA86OXhJYn__tZIs58jBoF4-RpYoRGaT-Plc36jg75HEaNJxgvCe05keEBD1g5fPD0IpRpwBKRnEybAPI-xFvzwKEMUSLR3Oz1nMuBn9KivqqrWu658Q2XCIvorn1eCBZJ8F-jcDoGErbsaNXpNL0qd6fw4pWnH8U8oyC4A-Jh26M9kmKGIGZ5EsJ64EDLgi5Rt_bNRodcLT8EJF5j88phRX3F_u0G00)

### Sequence diagram for read
![Sequence diagram for readt](https://img.plantuml.biz/plantuml/png/ZP5R2i8m44N_0zd3u9Ubk051KPTGiu5efpQGfZMP-jYzaxPgAoB-b7BdFWva4qXxQYihXJvIxM6MeSxHWoez_yn01yYK50sxpD4qsX7qZW_DkDEkBOgeIv4fMAm77omXDiqJbdiXdbvsFjSsnq3RYpPM7opkyVp3VFM6dXPcjjIrywb0Q-60fN6v502Z-1F3uOqugUgemEfpLmgCqRxKrMo_0czLOLmN-nHtOTNGdHCsO6S2_QwPZ8_PJqZRI32Va2J_uARfDuguF61l-ClF-000)