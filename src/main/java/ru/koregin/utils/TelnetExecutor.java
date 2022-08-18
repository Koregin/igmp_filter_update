package ru.koregin.utils;

import net.sf.expectit.Expect;
import net.sf.expectit.ExpectBuilder;
import org.apache.commons.net.telnet.TelnetClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.koregin.repo.SqlRepository;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

import static net.sf.expectit.matcher.Matchers.contains;

public class TelnetExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(SqlRepository.class.getName());
    private final String switchPassword;
    private final String switchEnablePassword;

    public TelnetExecutor() {
        try (InputStream in = TelnetExecutor.class.getClassLoader()
                .getResourceAsStream("app.properties")) {
            Properties config = new Properties();
            config.load(in);
            this.switchPassword = config.getProperty("switch-password");
            this.switchEnablePassword = config.getProperty("switch-enable-password");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Update igmp profile for abonent port
     *
     * @param switchIp
     * @param port
     * @param ipGroups
     */
    public boolean igmpProfileUpdate(int userId, String switchIp, int port, List<String> ipGroups) throws IOException {
        LOG.info("Обновляю igmp профиль абонента " + userId + ", коммутатор: " + switchIp + ", порт: " + port);
        TelnetClient telnet = new TelnetClient();
        telnet.setConnectTimeout(2000);
        try {
            telnet.connect(switchIp);
        } catch (Exception e) {
            LOG.error("The switch " + switchIp + " is not available");
            return false;
        }
        Expect expect = new ExpectBuilder()
                .withOutput(telnet.getOutputStream())
                .withInputs(telnet.getInputStream())
                .withExceptionOnFailure()
                .build();

        try {
            expect.expect(contains("Password:"));
            expect.sendLine(switchPassword).expect(contains(">"));
            expect.sendLine("terminal length 0").expect(contains(">"));
            expect.sendLine("en").expect(contains("Password:"));
            expect.sendLine(switchEnablePassword).expect(contains("#"));
        } catch (Exception e) {
            LOG.error("Проблема с авторизацией. Или это не Cisco коммутатор или пароли не стандартные");
            expect.close();
            return true;
        }
        /* Create igmp profile */
        expect.sendLine("conf t").expect(contains("(config)#"));
        expect.sendLine("no ip igmp profile " + userId).expect(contains("(config)#"));
        expect.sendLine("ip igmp profile " + userId).expect(contains("(config-igmp-profile)#"));
        expect.sendLine("permit").expect(contains("(config-igmp-profile)#"));
        expect.sendLine("range 239.255.1.254").expect(contains("(config-igmp-profile)#"));
        for (String ip : ipGroups) {
            expect.sendLine("range " + ip).expect(contains("(config-igmp-profile)#"));
        }
        expect.sendLine("end").expect(contains("#"));
        /* Set igmp profile on user port */
        expect.sendLine("conf t").expect(contains("(config)#"));
        expect.sendLine("int fa0/" + port).expect(contains("(config-if)#"));
        expect.sendLine("ip igmp filter " + userId).expect(contains("(config-if)#"));
        expect.sendLine("end").expect(contains("#"));
        expect.sendLine("write").expect(contains("#"));
        expect.sendLine("exit").close();
        LOG.info("Profile updated: userId=" + userId + ", switchIp=" + switchIp + ", port=" + port + ", GroupsSize=" + ipGroups.size());
        return true;
    }
}
