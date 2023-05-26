package main

import (
	"container/list"
	"fmt"
	"math"
	"net"
	"os"
	"os/signal"
	"strconv"
	"syscall"
	"time"
)

func main() {
	args := os.Args[1:]
	l := len(args)
	times := 10
	if l < 2 {
		showHelp()
		return
	}
	checkNum := false
	if l > 2 {
		for _, it := range args[:l-2] {
			if checkNum {
				i, err := strconv.Atoi(it)
				if err != nil {
					goto breakHere
				}
				times = i
				checkNum = false
				continue
			}
			if it == "-n" {
				checkNum = true
				continue
			}
			if it == "-t" {
				times = math.MaxInt
				continue
			}
		breakHere:
			showHelp()
		}
	}
	udpAddr, err := net.ResolveUDPAddr("udp", fmt.Sprintf("%s:%s", args[l-2], args[l-1]))
	if err != nil {
		fmt.Println("Fail to resolve address:", err)
		return
	}
	fmt.Println(fmt.Sprintf("\nPinging [%s] via UDP:", udpAddr))

	//创建socket
	conn, err := net.DialUDP("udp", nil, udpAddr)
	if err != nil {
		//无法解析对方地址
		fmt.Println("Fail :", err)
	}
	//协程通知
	interrupt := make(chan os.Signal, 1)
	stop := make(chan struct{}, 1)
	done := make(chan struct{}, 1)
	signal.Notify(interrupt, syscall.SIGINT, syscall.SIGKILL)
	//ping
	seq := time.Now().Unix()
	rttList := list.New()
	var success, sent, lost int
	go func() {
		for i := 0; i < times; i++ {
			select {
			case <-stop:
				done <- struct{}{}
				return
			default:
				now := time.Now()
				message := fmt.Sprintf("PingUDP %d %d\r\n", seq+int64(times-i), now.Unix())
				_, err = conn.Write([]byte(message))
				if err != nil {
					fmt.Println("Fail to send:", err)
					continue
				}
				sent++
				conn.SetReadDeadline(time.Now().Add(time.Second))
				buffer := make([]byte, 1024)
				length, _, err := conn.ReadFromUDP(buffer)
				if string(buffer[:length]) != message {
					fmt.Println("Timed out.")
					lost++
					continue
				}
				if err != nil {
					/*if netErr, ok := err.(net.Error); ok && netErr.Timeout() {
						fmt.Println("Timed out.")
					}*/
					fmt.Println("General error:", err)
					lost++
					continue
				}
				success++
				rtt := time.Since(now).Milliseconds()
				fmt.Println(fmt.Sprintf("Reply from [%s]: bytes=%d RTT=%dms", udpAddr, length, rtt))
				rttList.PushBack(rtt)
			}
		}
		done <- struct{}{}
	}()
	select {
	case <-interrupt:
		fmt.Println("control-c")
		stop <- struct{}{}
		<-done
	case <-done:
		fmt.Println()
		break
	}
	conn.Close()
	fmt.Println(fmt.Sprintf("Ping statistics for [%s]:", udpAddr))
	fmt.Println(fmt.Sprintf("\tPackets: Sent = %d, Received = %d, Lost = %d (%.0f%% loss),", sent, success, lost, (float64(lost)/float64(sent))*100))
	if rttList.Len() == 0 {
		return
	}
	// 遍历链表并计算最小值、最大值和总和
	min := rttList.Front().Value.(int64)
	max := min
	sum := max
	for e := rttList.Front().Next(); e != nil; e = e.Next() {
		value := e.Value.(int64)
		if value < min {
			min = value
		} else if value > max {
			max = value
		}
		sum += value
	}
	average := float64(sum) / float64(rttList.Len())
	fmt.Println("Approximate RTTs:")
	fmt.Println(fmt.Sprintf("\tMinimum = %dms, Maximun = %dms, Average = %.2fms", min, max, average))
}

func showHelp() {
	fmt.Println("udping [-t] [-n <times>] <ip> <port>")
	fmt.Println("-t : ping continuously until stopped via control-c")
	fmt.Println("-n 5 : for instance, send 5 pings")
}
