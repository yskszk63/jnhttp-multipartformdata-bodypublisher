package main

import (
	"fmt"
	"io"
	"log"
	"mime"
	"mime/multipart"
	"net/http"
	"context"
	"os"
	"bufio"
)

func main() {
	mux := http.NewServeMux()
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		fail := func(s string) {
			w.WriteHeader(400)
			log.Println(s)
		}

		ty, params, err := mime.ParseMediaType(r.Header.Get("Content-Type"))
		if err != nil || ty != "multipart/form-data" {
			fail("failed to parse Content-Type")
			return
		}
		boundary := params["boundary"]
		mr := multipart.NewReader(r.Body, boundary)

		var last int
		for i := 0; ; i++ {
			p, err := mr.NextPart()
			if err == io.EOF {
				break
			}
			if err != nil {
				fail("failed to parse part")
				return
			}

			slup, err := io.ReadAll(p)
			if err != nil {
				fail("failed to read part")
				return
			}

			expectGitignore := ".classpath\n.project\n.settings/\ntarget/\n"
			switch i {
			case 0:
				if p.FormName() != "key" || string(slup) != "value" {
					fail("assertion failed.")
					return
				}
			case 1:
				if p.FormName() != "f1" || string(slup) != expectGitignore || p.FileName() != ".gitignore" || p.Header.Get("Content-Type") != "application/octet-stream" {
					fail("assertion failed.")
					return
				}
			case 2:
				if p.FormName() != "f2" || string(slup) != expectGitignore || p.FileName() != ".gitignore" || p.Header.Get("Content-Type") != "text/plain" {
					fail("assertion failed.")
					return
				}
			case 3:
				if p.FormName() != "f3" || string(slup) != "a" || p.FileName() != "fname" || p.Header.Get("Content-Type") != "application/octet-stream" {
					fail("assertion failed.")
					return
				}
			case 4:
				if p.FormName() != "f4" || string(slup) != "b" || p.FileName() != "fname" || p.Header.Get("Content-Type") != "text/plain" {
					fail("assertion failed.")
					return
				}
			case 5:
				if p.FormName() != "f5" || string(slup) != "c" || p.FileName() != "fname" || p.Header.Get("Content-Type") != "application/octet-stream" {
					fail("assertion failed.")
					return
				}
			case 6:
				if p.FormName() != "f6" || string(slup) != "d" || p.FileName() != "fname" || p.Header.Get("Content-Type") != "text/plain" {
					fail("assertion failed.")
					return
				}
			default:
				fail("incorrect number of parts")
				return
			}
			last = i
		}

		if last != 6 {
			fail("few parts")
			return
		}

		w.WriteHeader(200)
	})

	srv := &http.Server {
		Addr: ":8080",
		Handler: mux,
	}

	go func() {
		if err := srv.ListenAndServe(); err != nil {
			log.Fatal(err)
		}
	}()
	fmt.Println("ready");

	stdin := bufio.NewScanner(os.Stdin)
	for stdin.Scan() {
		stdin.Text()
	}

	srv.Shutdown(context.Background())
}
