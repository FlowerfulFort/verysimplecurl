package com.flowerfulfort.curl;

import java.io.FileNotFoundException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class Main {
    static final String HELPER = """
            Usage: scurl [option] url
            Options:
            -v                  verbose, 요청, 응답 헤더를 출력한다.
            -H <line>           임의의 헤더를 서버로 전송한다.
            -d <data>           POST, PUT 등에 데이터를 전송한다.
            -X <command>        사용할 method를 지정한다. 지정되지 않은 경우, 기본값은 GET
            -L                  서버의 응답이 30x 계열이면 다음 응답을 따라 간다.
            -F <name=@[contentURI]>   multipart/form-data를 구성하여 전송한다.
                        """;

    public static void main(String[] args) {
        CommandLineParser parser = new DefaultParser();
        Options options = new Options();

        options.addOption("v", false, "verbose, 요청, 응답 헤더를 출력한다.");
        options.addOption("H", true, "임의의 헤더를 서버로 전송한다.");
        options.addOption("d", true, "POST, PUT 등에 데이터를 전송한다.");
        options.addOption("X", true, "사용할 method를 지정한다. 지정되지 않은 경우, 기본값은 GET");
        options.addOption("L", false, "서버의 응답이 30x 계열이면 다음 응답을 따라 간다.");
        options.addOption("F", true, "multipart/form-data를 구성하여 전송한다.");

        HttpRequest.Builder builder = HttpRequest.builder();
        if (args.length <= 0) {
            System.out.print(HELPER);
            return;
        }
        try {
            CommandLine cmd = parser.parse(options, args);
            String opt = null;
            // visibility 옵션
            if (cmd.hasOption("v")) {
                builder.setHeaderVisible();
            }
            // Method 옵션
            if ((opt = cmd.getOptionValue("X")) != null) {
                builder.setMethod(Method.valueOf(opt));
            }
            // data 전송 옵션
            if ((opt = cmd.getOptionValue("d")) != null) {
                builder.setData(opt);
            }
            // host 세팅
            String[] host = cmd.getArgs();
            if (host.length == 0) {
                System.err.println("host is missing.\n");
                System.exit(1);
            } else {
                builder.setHost(host[0]);
            }
            // file 옵션
            String[] files = null;
            if ((files = cmd.getOptionValues("F")) != null) {
                builder.setFiles(files);
            }
            // 커스텀 헤더
            String[] customHeaders = null;
            if ((customHeaders = cmd.getOptionValues("H")) != null) {
                builder.setCustomHeader(customHeaders);
            }
            // 리디렉션
            if (cmd.hasOption("L")) {
                builder.setRedirect();
            }
            HttpRequest request = builder.build();
            request.request();
        } catch (ParseException e) {
            System.out.print(HELPER);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            System.err.println("Files are not existing");
        }

    }
}