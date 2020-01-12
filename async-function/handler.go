package main

import (
	"log"
	"fmt"
	"os"
	nats "github.com/nats-io/nats.go"
)

type Config struct {
	host string
	port string
	topic string
}

func main() {
	config := getConfig()
	conn := connect(config)
	defer conn.Close()
	receive(conn, config.topic)
}

func getConfig() Config {
	return Config {
		host: getEnv("NATS_HOST", "localhost"),
		port: getEnv("NATS_PORT", "4222"),
		topic: getEnv("NATS_TOPIC", "test"),
	}
}

func getEnv(key, fallback string) string {
    value, exists := os.LookupEnv(key)
    if !exists {
        value = fallback
    }
    return value
}

func connect(config Config) *nats.Conn {
	connectionString := fmt.Sprintf("nats://%s:%s", config.host, config.port)
	nc, err := nats.Connect(connectionString)
	if err != nil {
		log.Fatalf("Could not connect to NATS server: %s", err.Error())
	}
	return nc
}

func receive(conn *nats.Conn, topic string) {
	ch := make(chan *nats.Msg, 64)
	_, err := conn.ChanSubscribe(topic, ch)
	if err != nil {
		log.Fatalf("Could not subscribe to topic %s: %s", topic, err)
	} else {
		log.Printf("Subscribed to topic %s", topic)
		for {
			msg := <- ch
			log.Printf("Received: %s", msg.Data)
		}	
	}
}