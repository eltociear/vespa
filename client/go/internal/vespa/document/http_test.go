package document

import (
	"bytes"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"reflect"
	"testing"
	"time"

	"github.com/vespa-engine/vespa/client/go/internal/mock"
)

type manualClock struct {
	t    time.Time
	tick time.Duration
}

func (c *manualClock) now() time.Time {
	t := c.t
	c.t = c.t.Add(c.tick)
	return t
}

func TestClientSend(t *testing.T) {
	docs := []Document{
		{Create: true, Id: mustParseId("id:ns:type::doc1"), Operation: OperationUpdate, Body: []byte(`{"fields":{"foo": "123"}}`)},
		{Create: true, Id: mustParseId("id:ns:type::doc2"), Operation: OperationUpdate, Body: []byte(`{"fields":{"foo": "456"}}`)},
		{Create: true, Id: mustParseId("id:ns:type::doc3"), Operation: OperationUpdate, Body: []byte(`{"fields":{"baz": "789"}}`)},
	}
	httpClient := mock.HTTPClient{}
	client := NewClient(ClientOptions{
		BaseURL: "https://example.com:1337",
		Timeout: time.Duration(5 * time.Second),
	}, &httpClient)
	clock := manualClock{t: time.Now(), tick: time.Second}
	client.now = clock.now
	for i, doc := range docs {
		if i < 2 {
			httpClient.NextResponseString(200, `{"message":"All good!"}`)
		} else {
			httpClient.NextResponseString(502, `{"message":"Good bye, cruel world!"}`)
		}
		res := client.Send(doc)
		if res.Err != nil {
			t.Fatalf("got unexpected error %q", res.Err)
		}
		r := httpClient.LastRequest
		if r.Method != http.MethodPut {
			t.Errorf("got r.Method = %q, want %q", r.Method, http.MethodPut)
		}
		wantURL := fmt.Sprintf("https://example.com:1337/document/v1/ns/type/docid/%s?create=true&timeout=5000ms", doc.Id.UserSpecific)
		if r.URL.String() != wantURL {
			t.Errorf("got r.URL = %q, want %q", r.URL, wantURL)
		}
		body, err := io.ReadAll(r.Body)
		if err != nil {
			t.Fatalf("got unexpected error %q", err)
		}
		wantBody := doc.Body
		if !bytes.Equal(body, wantBody) {
			t.Errorf("got r.Body = %q, want %q", string(body), string(wantBody))
		}
	}
	stats := client.Stats()
	want := Stats{
		Requests:  3,
		Responses: 3,
		ResponsesByCode: map[int]int64{
			200: 2,
			502: 1,
		},
		Errors:       1,
		Inflight:     0,
		TotalLatency: 3 * time.Second,
		MinLatency:   time.Second,
		MaxLatency:   time.Second,
		BytesSent:    75,
		BytesRecv:    82,
	}
	if !reflect.DeepEqual(want, stats) {
		t.Errorf("got %+v, want %+v", stats, want)
	}
}

func TestURLPath(t *testing.T) {
	tests := []struct {
		in  Id
		out string
	}{
		{
			Id{
				Namespace:    "ns-with-/",
				Type:         "type-with-/",
				UserSpecific: "user",
			},
			"/document/v1/ns-with-%2F/type-with-%2F/docid/user",
		},
		{
			Id{
				Namespace:    "ns",
				Type:         "type",
				Number:       ptr(int64(123)),
				UserSpecific: "user",
			},
			"/document/v1/ns/type/number/123/user",
		},
		{
			Id{
				Namespace:    "ns",
				Type:         "type",
				Group:        "foo",
				UserSpecific: "user",
			},
			"/document/v1/ns/type/group/foo/user",
		},
		{
			Id{
				Namespace:    "ns",
				Type:         "type",
				UserSpecific: "user::specific",
			},
			"/document/v1/ns/type/docid/user::specific",
		},
		{
			Id{
				Namespace:    "ns",
				Type:         "type",
				UserSpecific: ":",
			},
			"/document/v1/ns/type/docid/:",
		},
	}
	for i, tt := range tests {
		path := urlPath(tt.in)
		if path != tt.out {
			t.Errorf("#%d: documentPath(%q) = %s, want %s", i, tt.in, path, tt.out)
		}
	}
}

func TestClientFeedURL(t *testing.T) {
	tests := []struct {
		in     Document
		method string
		url    string
	}{
		{
			Document{Id: mustParseId("id:ns:type::user")},
			"POST",
			"https://example.com/document/v1/ns/type/docid/user?foo=ba%2Fr",
		},
		{
			Document{
				Id:        mustParseId("id:ns:type::user"),
				Operation: OperationUpdate,
				Create:    true,
				Condition: "false",
			},
			"PUT",
			"https://example.com/document/v1/ns/type/docid/user?condition=false&create=true&foo=ba%2Fr",
		},
		{
			Document{
				Id:        mustParseId("id:ns:type::user"),
				Operation: OperationRemove,
			},
			"DELETE",
			"https://example.com/document/v1/ns/type/docid/user?foo=ba%2Fr",
		},
	}
	httpClient := mock.HTTPClient{}
	client := NewClient(ClientOptions{
		BaseURL: "https://example.com",
	}, &httpClient)
	for i, tt := range tests {
		moreParams := url.Values{}
		moreParams.Set("foo", "ba/r")
		method, u, err := client.feedURL(tt.in, moreParams)
		if err != nil {
			t.Errorf("#%d: got unexpected error = %s, want none", i, err)
		}
		if u.String() != tt.url || method != tt.method {
			t.Errorf("#%d: URL() = (%s, %s), want (%s, %s)", i, method, u.String(), tt.method, tt.url)
		}
	}
}
