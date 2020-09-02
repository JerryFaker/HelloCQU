import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Recur;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.property.*;
import net.fortuna.ical4j.util.RandomUidGenerator;
import net.fortuna.ical4j.util.UidGenerator;
import org.apache.commons.codec.digest.DigestUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//一个能够登陆教务网，抓取课程表，并生成iCal格式日程的小应用

public class jwc {

    public static void main(String[] args) {
        try {
            Scanner scanner = new Scanner(System.in);
            System.out.println("请输入学号：");
            stuID = scanner.nextLine();
            System.out.println("请输入教务网密码：");
            pwd = scanner.nextLine();
            System.out.println("请按照格式输入当前学期：");
            System.out.println("e.g.: 20200 -> 2020-2021学年第一学期");
            System.out.println("20191 ->2019-2020学年第二学期");
            semester = scanner.nextLine();
            System.out.println("请按照格式(yyyy/MM/dd)输入本学期第一周星期一的日期：(e.g.:2020/08/31）");
            String s = scanner.nextLine();
            System.out.println("是否要保留课程名称前的编号？1/0");
            isCourseNo = scanner.nextLine().equals("1");
            System.out.println("正在登陆重庆大学教务网...");
            jwc jwc = new jwc(stuID,pwd);//登陆教务网，获得cookie
            System.out.println("正在获取课程表...");
            ArrayList<String[]> list = jwc.filter(formatKCB(jwc.getKCB()));//从教务网获得课程表，并格式化妥当
            System.out.println("所有课程预览：");
            for(String[] newList : list) {
                System.out.println("--------"+newList[0]+" "+newList[1]+" "+newList[2]+" "+newList[3]+" "+newList[4]);
            }
            System.out.println("是否生成.ics格式的日历文件？1/0");
            if (scanner.nextLine().equals("1")) {
                iCal cal = new iCal(list,s);
                cal.printCal(stuID+"课程表");
            }
            System.out.println("请检查目录中是否有.ics文件生成");
            System.out.println("Written by Jerry.F");
            if (scanner.hasNext()) {
                System.exit(0);
            }
        } catch (Exception e) {
            System.out.println("！！！！出现了无法预料的错误，可能是你输错了数字，也有可能是我写出了bug，请重启程序");
        }

    }

    //以下信息用于登录教务网抓取信息
     static String semester = "20200";//0代表第一学期，1代表第二学期
     static String stuID = "";
     static String pwd = "";
    //以下信息就不要随便改了吧
    final String loginURL = "http://202.202.1.41/_data/index_login.aspx";
    final String kcbURL = "http://202.202.1.41/znpk/Pri_StuSel_rpt.aspx";
    final String examURL = "http://202.202.1.41/KSSW/stu_ksap_rpt.aspx";
    final String schoolCode = "10611";
    private Map<String,String> cookies = new HashMap<>();
    //以下信息用于生成iCal格式的日历表
    static boolean isCourseNo = false; //日程名是课程名，是否需要前缀课程编号？

    //创建一个新的jwc class object时，使用学号和密码登录教务网，保留cookie
    public jwc(String stuID, String password) {
        HashMap<String, String> postTable = new HashMap<>();
        postTable.put("Sel_Type","STU");
        postTable.put("txt_dsdsdsdjkjkjc",stuID);
        postTable.put("efdfdfuuyyuuckjg", md5.getCode(stuID,password,schoolCode));
        try {
            Connection.Response a = Jsoup.connect(loginURL)
                    .method(Connection.Method.POST)
                    .data(postTable)
                    .execute();
            cookies = a.cookies();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //获得课程表，返回一个jSoup Document
    public Document getKCB() {
        Connection.Response kcb = null;
        try {
            kcb = Jsoup.connect(kcbURL)
                    .cookies(cookies)
                    .data("Sel_XNXQ",semester)
                    .data("rad","on")
                    .data("px","1")
                    .method(Connection.Method.POST)
                    .execute();
            return kcb.parse();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    //考试列表，返回一个jSoup Document
    public Document getExamList() {
        Connection.Response kcb = null;
        try {
            kcb = Jsoup.connect(examURL)
                    .cookies(cookies)
                    .data("sel_xnxq",semester)
                    .method(Connection.Method.POST)
                    .execute();
            return kcb.parse();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    //从jSoup HTML Document里抓取课程表内容，并转换成一个课程信息的嵌套List
    public static ArrayList<ArrayList<String>> formatKCB(Document syllabus) {
        ArrayList<ArrayList<String>> courses = new ArrayList<ArrayList<String>>();
        for (Element row : syllabus.select("table.page_table tr")) {
            Pattern p = Pattern.compile("(?<=>).*?(?=<br><\\/td>)");
            Matcher m = p.matcher(row.html());
            ArrayList<String> courseDetail = new ArrayList<String>();
            while( m.find() ) {
                String s = m.group();
                courseDetail.add(s);
            }
            if(!courseDetail.isEmpty()&&!courseDetail.get(0).equals("序号") && !courseDetail.get(0).equals(""))
                courses.add(courseDetail);
        }
        return courses;
    }

    //进一步处理课程嵌套数组内容，真正切碎成可以直接使用的信息
    public ArrayList<String[]> filter(ArrayList<ArrayList<String>> list) {
        //重要的是后四项：任课教师，周次，节次，教室和第二项：课程编号+课程名
        //如果一个list的第二项为空，那么向前搜寻，直到有课程名的数列，应用它的名字
        ArrayList<String[]> filteredList = new ArrayList<>();
        int j = list.size();
        for (int i = 0; i < j; i++) {
            ArrayList<String> unfiltered = list.get(i);
            //这里需要动一个手脚，为了后面方便处理，如果周数不连贯，需要作多个科目处理
            for(String s: unfiltered.get(unfiltered.size()-3).split(",")) {
                String[] newList = new String[5];
                newList[0] = filterName(unfiltered.get(1).equals("") ? checkName(list, i) : unfiltered.get(1));
                newList[1] = unfiltered.get(unfiltered.size()-4);
                newList[2] = s;
                newList[3] = unfiltered.get(unfiltered.size()-2);
                newList[4] = unfiltered.get(unfiltered.size()-1);
                filteredList.add(newList);
                //数组的内容顺序为：课程名，教师名，周数，星期几和节次，教室
            }
        }
        return filteredList;
    }

    //同一个课程可能会有多个日程，检查raw unfiltered list中没有名字的课程，将其赋予上一个课程的名字
    private String checkName(ArrayList<ArrayList<String>> list, int i) {
        i--;
        if (list.get(i).get(1).equals(""))
            return checkName(list, i);
        return list.get(i).get(1);
    }

    //可选课程名是否保留课程序号
    private String filterName(String s) {
        return isCourseNo ? s : s.split("]")[1];
    }

    //内部的静态class，用来处理所有与生成iCal文件有关的内容
    private static class iCal {
        Calendar calendar = new Calendar();
        String beginningOfTheSemester = "2020/08/31";
        TimeZone timeZone = TimeZone.getTimeZone("GMT+:08:00");

        public iCal(ArrayList<String[]> list, String s) {
            beginningOfTheSemester = s;
            calendar.getProperties().add(new ProdId("-//Ben Fortuna//iCal4j 1.0//EN"));
            calendar.getProperties().add(Version.VERSION_2_0);
            calendar.getProperties().add(CalScale.GREGORIAN);
            for (String[] course : list) {
                setCourse(course);
            }
            //System.out.println(calendar);

        }

        public void printCal(String filename) {
            try {
                FileOutputStream outputStream = new FileOutputStream(System.getProperty("user.dir")+"/"+filename+".ics");
                CalendarOutputter outputter = new CalendarOutputter();
                outputter.output(calendar,outputStream);
                System.out.println("日历文件应该已经生成完毕了");
            } catch (Exception e) {
                System.out.println("OOPS！");
            }
        }

        private void setCourse(String[] s) {
            //这一部分真的太dirty了
            String courseName = s[0];
            String location = s[4];
            String tutor = s[1];
            String weekDay = setWeekday(s[3].substring(0,1));
            String[] iterationOption = s[2].split("-");

            //处理课程时间（小时和分钟）
            String[] temp;
            String[] tempa = new String[2];
            tempa[0] = "0";
            tempa[1] = "0";
            temp = s[3].split("-").length == 1 ? tempa : s[3].split("-"); //论一个人为了不想写regex能有多拼命
            //System.out.println(temp.length + " " + temp[0]);
            //System.out.println(temp[0]+" "+temp[1]);
            int[] startCourseTime = setTime(temp[0].split("\\[").length == 1 ? temp[0].split("\\[")[0] : temp[0].split("\\[")[1]);
            //System.out.println(temp[1].split("节").length);
            int[] endCourseTime = setTime(temp[1].split("节")[0]);

            //处理教学周第一周的日期
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy/MM/dd");
            int firstWeekOfTheYear = 0;
            try {
                Date firstDay = simpleDateFormat.parse(beginningOfTheSemester);
                java.util.Calendar c = java.util.Calendar.getInstance();
                c.setTime(firstDay);
                firstWeekOfTheYear = c.get(java.util.Calendar.WEEK_OF_YEAR);
            } catch (ParseException e) {
                e.printStackTrace();
            }
            //将好不容易处理好的时间数据赶紧存到date里面
            java.util.Calendar startDate = new GregorianCalendar();
            java.util.Calendar endDate = new GregorianCalendar();
            startDate.set(java.util.Calendar.DAY_OF_WEEK, Integer.parseInt(weekDay));
            startDate.set(java.util.Calendar.HOUR_OF_DAY, startCourseTime[0]);
            startDate.set(java.util.Calendar.MINUTE, startCourseTime[1]);
            startDate.set(java.util.Calendar.SECOND, 0);
            startDate.set(java.util.Calendar.WEEK_OF_YEAR, firstWeekOfTheYear);
            startDate.add(java.util.Calendar.WEEK_OF_YEAR, Integer.parseInt(iterationOption[0])-1);
            endDate.set(java.util.Calendar.DAY_OF_WEEK, Integer.parseInt(weekDay));
            endDate.set(java.util.Calendar.HOUR_OF_DAY, endCourseTime[0]);
            endDate.set(java.util.Calendar.MINUTE, endCourseTime[1]);
            endDate.add(java.util.Calendar.MINUTE, 45);//下课时间要相比上课offset45分钟
            endDate.set(java.util.Calendar.SECOND, 0);
            endDate.set(java.util.Calendar.WEEK_OF_YEAR, firstWeekOfTheYear);
            endDate.add(java.util.Calendar.WEEK_OF_YEAR, Integer.parseInt(iterationOption[0])-1);

            //设立日程
            DateTime start = new DateTime(startDate.getTime());
            DateTime end = new DateTime(endDate.getTime());
            VEvent courseEvent = new VEvent(start,end,courseName);
            Location summary = new Location();
            summary.setValue(location);
            Description description = new Description();
            description.setValue(tutor);
            //System.out.println(description.getValue());
            courseEvent.getProperties().add(summary);
            courseEvent.getProperties().add(description);

            //处理按周重复的课程问题，设置RRULE
            if (iterationOption.length == 2) {
                int recurCount = Integer.parseInt(iterationOption[1])-Integer.parseInt(iterationOption[0]);
                Recur recur = new Recur(Recur.WEEKLY,recurCount);
                RRule rule = new RRule(recur);
                courseEvent.getProperties().add(rule);
            }

            //设置Property UID
            RandomUidGenerator ug = new RandomUidGenerator();
            Uid uid = ug.generateUid();
            courseEvent.getProperties().add(uid);
            //System.out.println(courseEvent);
            calendar.getComponents().add(courseEvent);

        }

        private int getWeekNo(Date date) {
            //输入第一周的日期，获得第一周是全年第多少周
            //以此为依托，应用教学周的数据
            return 0;
        }

        private String setWeekday(String weekDay) {
            switch (weekDay) {
                case "一":
                    weekDay = "2";
                    break;
                case "二":
                    weekDay = "3";
                    break;
                case "三":
                    weekDay = "4";
                    break;
                case "四":
                    weekDay = "5";
                    break;
                case "五":
                    weekDay = "6";
                    break;
                case "六":
                    weekDay = "7";
                    break;
                case "日":
                case "天":
                    weekDay = "1";
                    break;
            }
            return weekDay;
        }
        private int[] setTime(String s) {
            //对应节次的时间，以2020-2021年第一学期开始施行的最新时间表为准，全部校区统一
            int[] time = new int[2];//数组0对应小时，1对应分钟
            switch (s) {
                case "1":
                    time[0] = 8;
                    time[1] = 30;
                    break;
                case "2":
                    time[0] = 9;
                    time[1] = 25;
                    break;
                case "3":
                    time[0] = 10;
                    time[1] = 30;
                    break;
                case "4":
                    time[0] = 11;
                    time[1] = 25;
                    break;
                case "5":
                    time[0] = 13;
                    time[1] = 30;
                    break;
                case "6":
                    time[0] = 14;
                    time[1] = 25;
                    break;
                case "7":
                    time[0] = 15;
                    time[1] = 20;
                    break;
                case "8":
                    time[0] = 16;
                    time[1] = 25;
                    break;
                case "9":
                    time[0] = 17;
                    time[1] = 20;
                    break;
                case "10":
                    time[0] = 19;
                    time[1] = 0;
                    break;
                case "11":
                    time[0] = 19;
                    time[1] = 55;
                    break;
                case "12":
                    time[0] = 20;
                    time[1] = 50;
                    break;
                default:
                    time[0] = 23;
                    time[1] = 0;
                    break;
            }
        return time;
        }
    }



    //用于md5转换的工具类，登陆教务网时有用
    public static class md5 {
        private static String md5fy(String s) {
            return DigestUtils.md5Hex(s);
        }
        public static String getCode(String studentID, String password, String schoolCode) {
            return md5fy(studentID+md5fy(password).substring(0,30).toUpperCase()+schoolCode).substring(0,30).toUpperCase();
        }
    }

        /*获取成绩相关内容还没做完
    //获取成绩
    public Document getScore() {
        Connection.Response kcb = null;
        try {
            kcb = Jsoup.connect("http://202.202.1.41/xscj/Stu_MyScore_rpt.aspx")
                    .cookies(cookies)
                    .data("sel_xn","2019")
                    .data("sel_xq","0")
                    .data("SJ","1")
                    .data("SelXNXQ","2")
                    .method(Connection.Method.POST)
                    .execute();
            return kcb.parse();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


    //通过老教务网
    public static Document getAllScore(String usrID, String password) {
        //密码默认是身份证后六位
        try {
            Connection.Response a = Jsoup.connect("http://oldjw.cqu.edu.cn:8088/login.asp")
                    .method(Connection.Method.POST)
                    .data("username",usrID)
                    .data("password",password)
                    .data("submit1.x","19")
                    .data("submit1.y","8")
                    .data("select1","#")
                    .execute();
            Map<String,String> c = a.cookies();
            Connection.Response b = Jsoup.connect("http://oldjw.cqu.edu.cn:8088/score/sel_score/sum_score_sel.asp")
                    .method(Connection.Method.GET)
                    .cookies(c)
                    .execute();
            return b.parse();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

     */
}
