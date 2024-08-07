package com.flowerfulfort.curl;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

public class HttpRequest {
    private String originHost;
    private String host;
    private String location;
    private Method method;
    private String data;
    private int port;

    // multiparts 파일을 전송하는 용도.
    private boolean sendFile;
    private List<File> files;
    private List<String> fileAlias;
    private int fileLen;

    // fileAlias의 인덱스를 지정하는 용도.
    private int fileCounter;

    private int redirectCounter;
    private boolean visible;

    private String[] customHeader;

    private boolean redirect;

    // 파일 바운더리
    // 바운더리가 어떻게 생성되는지 몰라 고정.
    private static final String BOUNDARY = "------------------------cmeNT2ZxyH1uAG6jCp0boL";

    // 헤더 포맷
    private static final String GET_HEADER_FORMAT = "%s %s HTTP/1.1\r\nHost: %s\r\nUser-Agent: curl/1.0.0\r\nAccept: */*\r\n%s\r\n";
    private static final String POST_HEADER_FORMAT = "%s %s HTTP/1.1\r\nHost: %s\r\nUser-Agent: curl/1.0.0\r\nAccept: */*\r\n%sContent-Length: %d\r\n\r\n%s";
    // private static final String POST_HEADER_FORMAT = "%s %s HTTP/1.1\r\nHost:
    // %s\r\nUser-Agent: curl/1.0.0\r\nAccept: */*\r\n%sContent-Length:
    // %d\r\nContent-Type: application/x-www-form-urlencoded\r\n\r\n%s";
    private static final String FILE_HEADER_FORMAT = "POST %s HTTP/1.1\r\nHost: %s\r\nUser-Agent: curl/1.0.0\r\nAccept: */*\r\n%sContent-Length: %d\r\nContent-Type: multipart/form-data; boundary=%s\r\n\r\n";
    private static final String FILE_SUBHEADER_FORMAT = "Content-Disposition: form-data; name=\"%s\"; filename=\"%s\"\r\nContent-Type: text/plain\r\n\r\n";
    private static final String ENDLINE = "\r\n";
    private static final String LITTLE_BOUND = "--";

    HttpRequest(String host, Method method, String data) {
        visible = false;
        this.originHost = host;
        this.method = method;
        this.data = data;
        this.sendFile = false;
        redirect = false;
        fileLen = 0;
        fileCounter = 0;
        redirectCounter = 0;
        String httpProto = null;
        StringTokenizer stoken = new StringTokenizer(host, ":");
        if (host.startsWith("http")) {
            /* http(s) */
            httpProto = stoken.nextToken();
        } else {
            throw new IllegalArgumentException();
        }

        /* //www.example.com */
        stoken.nextToken();

        /* www.example.com:8080 */
        if (stoken.hasMoreTokens()) { // 포트번호가 명시가 되어 있는 경우.
            String tok = stoken.nextToken();
            tok = stoken.nextToken();
            int from = 0;
            int to = from;
            while (to < tok.length()) {
                if (!Character.isDigit(tok.charAt(to))) {
                    // 숫자가 아니면
                    break;
                } else
                    to++;
            }
            if (to < 0) { // 포트번호가 명시되어 있지 않는 예외상황.
                throw new IllegalArgumentException();
            }
            port = Integer.parseInt(tok.substring(from, to));
        } else if (httpProto == null) { // 포트번호도 없고 http 프로토콜 명시도 없는 경우.
            port = 80; // 기본은 HTTP
        } else { // 프로토콜 명시가 있는 경우.
            port = switch (httpProto) {
                case "http" -> 80;
                case "https" -> 443;
                default -> throw new IllegalArgumentException();
            };
        }
        parse();
    }

    // Builder
    static final class Builder {
        private String host;
        private Method method;
        private String data;
        private boolean visible;
        private String[] customHeader;
        private ArrayList<File> files;
        private ArrayList<String> fileAlias;
        private boolean sendFile;
        private boolean redirect;

        Builder() {
            // default values.
            method = Method.GET;
            data = null;
            visible = false;
            customHeader = null;
            sendFile = false;
            files = new ArrayList<>();
            fileAlias = new ArrayList<>();
            redirect = false;
        }

        public Builder setHost(String host) {
            this.host = host;
            return this;
        }

        public Builder setMethod(Method method) {
            this.method = method;
            return this;
        }

        public Builder setData(String data) {
            this.data = data;
            return this;
        }

        public Builder setHeaderVisible() {
            visible = true;
            return this;
        }

        public Builder setCustomHeader(String[] header) {
            customHeader = header;
            return this;
        }

        public Builder setRedirect() {
            redirect = true;
            return this;
        }

        public Builder setFiles(String[] f) throws FileNotFoundException {
            if (f.length <= 0)
                throw new IllegalArgumentException();
            sendFile = true;
            for (String fs : f) {
                String[] tok = fs.split("=@");
                // StringTokenizer tok = new StringTokenizer(fs, "=");
                fileAlias.add(tok[0]);
                // String filename = tok.nextToken();
                // filename = filename.substring(1, filename.length());

                File fileInst = new File(tok[1]);
                if (!fileInst.exists())
                    throw new FileNotFoundException();
                files.add(fileInst);
            }
            return this;
        }

        public HttpRequest build() {
            if (host == null) {
                throw new IllegalArgumentException();
            }
            HttpRequest req = new HttpRequest(host, method, data);
            req.visible = visible;

            if (sendFile) {
                req.sendFile = true;
                req.fileAlias = Collections.unmodifiableList(fileAlias);
                req.files = Collections.unmodifiableList(files);
            }
            req.customHeader = customHeader;
            req.redirect = redirect;
            return req;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    // host와 location을 분리.
    private void parse() {
        StringTokenizer stoken = new StringTokenizer(originHost, "/");
        if (originHost.startsWith("http")) {
            /* http(s):// 을 먼저 떼어냄. */
            stoken.nextToken();
        }
        host = stoken.nextToken();
        if (stoken.hasMoreTokens()) {
            StringBuilder sb = new StringBuilder();
            while (stoken.hasMoreTokens()) {
                sb.append('/').append(stoken.nextToken());
            }
            location = sb.toString();
        } else {
            location = "/";
        }
    }

    // 헤더를 생성하는 메소드
    private String createHeader() {
        int contentLength = 0;
        if (data != null) {
            contentLength = data.length();
        }
        StringBuilder cHeader = new StringBuilder("");
        boolean contentFlag = false;
        if (customHeader != null) {
            for (String h : customHeader) {
                if (h.startsWith("Content-Type"))
                    contentFlag = true;
                cHeader.append(h).append(ENDLINE);
            }
        }
        if ((method == Method.POST || method == Method.PUT) && !contentFlag) {
            cHeader.append("Content-Type: application/x-www-form-urlencoded").append(ENDLINE);
        }
        switch (method) {
            case GET:
                return String.format(GET_HEADER_FORMAT, method, location, host, cHeader.toString());
            case PUT:
            case POST:
            case DELETE:
            case HEAD:
                return data != null
                        ? String.format(POST_HEADER_FORMAT, method, location, host, cHeader.toString(), contentLength,
                                data)
                        : String.format(GET_HEADER_FORMAT, method, location, host, cHeader.toString());
            default:
                throw new IllegalArgumentException();

        }
    }

    public static Map<String, String> parseHeader(String header) {
        String[] headers = header.split("\n");
        HashMap<String, String> h = new HashMap<>();

        String[] httpRequest = headers[0].split(" ");
        h.put("HTTPVersion", httpRequest[0]);
        h.put("StatusCode", httpRequest[1]);
        h.put("Status", httpRequest[2]);

        for (int i = 1; i < headers.length; i++) {
            StringTokenizer tok = new StringTokenizer(headers[i], ":");
            String key = tok.nextToken().strip();
            String value = tok.nextToken().strip();
            h.put(key, value);
        }
        return h;
    }

    public void request() {
        if (sendFile) {
            requestMultipart();
        } else {
            requestNormally();
        }
    }

    private void requestNormally() {
        if (redirectCounter > 5) {
            System.err.println("Redirection loop detected.");
            System.err.println("Application Terminated");
            System.exit(1);
        }
        // System.out.println(host);
        try (Socket sc = new Socket(host, port)) {
            String sendHeader = createHeader();
            if (visible) { // -v 옵션
                System.out.printf("* Connected to %s (%s) port %d%n", host, sc.getInetAddress(), sc.getPort());
                String[] lines = sendHeader.split("\r\n");
                for (String s : lines) {
                    System.out.print("> ");
                    System.out.println(s);
                }
                System.out.println("> ");
            }

            // 헤더(와 데이터)를 전송
            sc.getOutputStream().write(sendHeader.getBytes());

            // response를 받음.
            BufferedReader br = new BufferedReader(new InputStreamReader(sc.getInputStream()));
            String str = null;
            StringBuilder sb = new StringBuilder();
            while ((str = br.readLine()) != null) {
                if (str.isEmpty())
                    break;
                sb.append(str).append('\n');
            } // header 받기
            if (visible) {
                System.out.println("* Request completely sent off");
                String[] receivedHeader = sb.toString().split("\n");
                for (String s : receivedHeader) {
                    System.out.print("< ");
                    System.out.println(s);
                }
                System.out.println("< ");
            }
            sb.deleteCharAt(sb.length() - 1);
            Map<String, String> header = parseHeader(sb.toString());

            // 만약 30x redirect 라면...
            int statusCode = Integer.parseInt(header.get("StatusCode"));
            String next = header.get("Location");
            if (redirect && statusCode >= 300 && statusCode < 400 && next != null) {
                if (next.startsWith("http")) {
                    // 다른 도메인으로 갈 경우..
                    originHost = next;
                    parse();
                } else { // 도메인 내의 location만 지시하는 경우.
                    location = next;
                }
                redirectCounter++;
                requestNormally(); // location을 바꾸고 다시 request.
            } else {
                String ctype = header.get("Content-Type");
                // Content-Type을 체크하여 텍스트와 json 데이터만 출력.
                if (ctype != null && (ctype.startsWith("text/") || ctype.equals("application/json"))) {
                    // 헤더의 Content-Length를 읽어 그만큼 읽음.
                    int bodyLength = Integer.parseInt(header.get("Content-Length"));

                    char[] buf = new char[bodyLength];
                    br.read(buf);
                    // 표준 출력으로 출력.
                    System.out.println(buf);
                }
                if (visible) {
                    System.out.printf("Connection to host %s left intact%n", host);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Cannot connect to host\n");
        }
    }

    private void requestMultipart() {
        if (redirectCounter > 5) {
            System.err.println("Redirection loop detected.");
            System.err.println("Application Terminated");
            System.exit(1);
        }
        if (files != null) {
            try {
                fileLen = 0;
                // 파일과 boundary를 쌓기위한 파이프스트림.
                PipedInputStream pin = new PipedInputStream();
                PipedOutputStream pout = new PipedOutputStream(pin);
                BufferedWriter bpout = new BufferedWriter(new OutputStreamWriter(pout));

                // FileReader는 character 단위로 read하기 때문에 output도 맞춤.
                OutputStreamWriter poutput = new OutputStreamWriter(pout);

                // bound가 "--"를 붙이고 설정된 boundary를 붙임.
                bpout.write(LITTLE_BOUND);
                bpout.write(BOUNDARY);
                bpout.flush();
                fileLen += (BOUNDARY.length() + LITTLE_BOUND.length());

                files.stream().forEach(file -> {
                    try (FileReader fr = new FileReader(file)) {
                        bpout.write(ENDLINE);
                        // 파일의 서브헤더를 설정.
                        String subHeader = String.format(FILE_SUBHEADER_FORMAT, fileAlias.get(fileCounter++),
                                file.getName());
                        bpout.write(subHeader);
                        fileLen = fileLen + ENDLINE.length() + subHeader.length();
                        bpout.flush(); // 길이를 계산하고 flush.

                        int ch = 0;
                        // 파일을 읽어 파이프에 write.
                        while ((ch = fr.read()) != -1) {
                            poutput.write(ch);
                            fileLen++;
                        }
                        poutput.flush();
                        // 파일 끝 바운더리
                        bpout.write(ENDLINE);
                        bpout.write(LITTLE_BOUND);
                        bpout.write(BOUNDARY);
                        bpout.flush();
                        fileLen = fileLen + ENDLINE.length() + BOUNDARY.length() + LITTLE_BOUND.length();
                    } catch (IOException exp) {
                        exp.printStackTrace();
                        System.err.println("File IO Error\n");
                        System.exit(1);
                    }
                });
                bpout.write(LITTLE_BOUND);
                fileLen += LITTLE_BOUND.length();
                bpout.write(ENDLINE);
                fileLen += ENDLINE.length();
                bpout.flush();

                try (Socket sc = new Socket(host, port)) {
                    // custom Header.
                    StringBuilder cHeader = new StringBuilder("");
                    if (customHeader != null) {
                        for (String h : customHeader) {
                            cHeader.append(h).append(ENDLINE);
                        }
                    }

                    // 파일 버퍼를 분할할 수 있도록 만드는게 좋을듯..
                    char[] fileBuf = new char[fileLen];
                    // 전송헤더
                    String sendHeader = String.format(FILE_HEADER_FORMAT, location, host, cHeader.toString(), fileLen,
                            BOUNDARY);
                    if (visible) {
                        System.out.printf("* Connected to %s (%s) port %d%n", host, sc.getInetAddress(), sc.getPort());
                        String[] lines = sendHeader.split("\r\n");
                        for (String s : lines) {
                            System.out.print("> ");
                            System.out.println(s);
                        }
                        System.out.println("> ");
                    }
                    // (파일과 바운더리가 쌓인)파이프에서 char 단위로 읽어들임.
                    BufferedReader pinput = new BufferedReader(new InputStreamReader(pin));
                    pinput.read(fileBuf);

                    BufferedWriter socketOut = new BufferedWriter(new OutputStreamWriter(sc.getOutputStream()));

                    // 헤더를 먼저 전송.
                    sc.getOutputStream().write(sendHeader.getBytes());
                    // 파일 데이터를 전송.
                    socketOut.write(fileBuf);
                    socketOut.flush();

                    // 응답을 받음.
                    BufferedReader br = new BufferedReader(new InputStreamReader(sc.getInputStream()));
                    String str = null;
                    StringBuilder sb = new StringBuilder();
                    while ((str = br.readLine()) != null) {
                        if (str.isEmpty())
                            break;
                        sb.append(str).append('\n');
                    } // header 받기

                    if (visible) {
                        System.out.println("* Request completely sent off");
                        String[] receivedHeader = sb.toString().split("\n");
                        for (String s : receivedHeader) {
                            System.out.print("< ");
                            System.out.println(s);
                        }
                        System.out.println("< ");
                    }
                    sb.deleteCharAt(sb.length() - 1);
                    Map<String, String> header = parseHeader(sb.toString());
                    // 만약 30x redirect 라면...
                    if (redirect && header.get("Status").equals("FOUND")) {
                        String next = header.get("Location");
                        if (next.startsWith("http")) {
                            // 다른 도메인으로 갈 경우..
                            originHost = next;
                            parse();
                        } else { // 도메인 내의 location만 지시하는 경우.
                            location = next;
                        }
                        redirectCounter++;
                        requestMultipart(); // location을 바꾸고 다시 request.
                    } else {
                        String ctype = header.get("Content-Type");
                        // Content-Type을 체크하여 텍스트와 json 데이터만 출력.
                        if (ctype != null && (ctype.startsWith("text/") || ctype.equals("application/json"))) {
                            int bodyLength = Integer.parseInt(header.get("Content-Length"));

                            char[] buf = new char[bodyLength];
                            br.read(buf);
                            System.out.println(buf);
                        }
                        if (visible) {
                            System.out.printf("Connection to host %s left intact%n", host);
                        }
                        poutput.close();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
