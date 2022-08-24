package ru.koregin.repo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.koregin.model.Abonent;

import java.io.InputStream;
import java.sql.*;
import java.util.*;

public class SqlRepository implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SqlRepository.class.getName());

    private Connection cn;

    public static void main(String[] args) {
        int abId = 5863;
        try (SqlRepository store = new SqlRepository()) {
            store.init();
            System.out.println(store.checkBlockAbonent(abId) ? "Нет блокировки" : "Заблокирован");
            System.out.println(store.checkBalance(abId) ? "Баланс норм" : "Баланс меньше нуля");
            System.out.println(store.checkWriteOffsCurrentMonth(abId) ? "Списание в этом месяце было" : "Списания не было");
            System.out.println("IP Groups: " + store.getIpGroupsForAbonent(abId));
            store.setCiscoJob(abId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void init() {
        try (InputStream in = SqlRepository.class.getClassLoader()
                .getResourceAsStream("app.properties")) {
            Properties config = new Properties();
            config.load(in);
            Class.forName(config.getProperty("driver-class-name"));
            cn = DriverManager.getConnection(
                    config.getProperty("url"),
                    config.getProperty("username"),
                    config.getProperty("password")
            );
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Check for abonent block
     *
     * @return if block = 1 then false, else true
     */
    public boolean checkBlockAbonent(int id) {
        int blockResult = 0;
        try (PreparedStatement ps =
                     cn.prepareStatement("SELECT block FROM abonents where id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    blockResult = rs.getInt("block");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        LOG.debug("Check block abonent: " + blockResult);
        return blockResult != 1;
    }

    /**
     * Check abonent balance
     *
     * @param id abonent
     * @return true if balance >= 0
     */
    public boolean checkBalance(int id) {
        int balanceResult = -1;
        try (PreparedStatement ps =
                     cn.prepareStatement("SELECT balance FROM abonents where id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    balanceResult = rs.getInt("balance");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        LOG.debug("Balance user " + id + " :" + balanceResult);
        return balanceResult >= 0;
    }

    /**
     * Check writeOffs in current month
     *
     * @param id abonent
     * @return true if writeOffs were
     */
    public boolean checkWriteOffsCurrentMonth(int id) {
        int writeOffsResilt = 0;
        try (PreparedStatement ps =
                     cn.prepareStatement("SELECT COUNT(id) writeoffs "
                             + "FROM events "
                             + "WHERE user = ? "
                             + "AND YEAR(date) = YEAR(CURDATE()) "
                             + "AND action = 0 "
                             + "AND object = MONTH(CURDATE())")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    writeOffsResilt = rs.getInt("writeoffs");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        LOG.debug("User writeOffs " + id + " :" + writeOffsResilt);
        return writeOffsResilt > 0;
    }

    /**
     * Check new connection in current month
     *
     * @param id abonent
     * @return true if connection was
     */
    public boolean checkNewConnectionCurrentMonth(int id) {
        int connection = 0;
        try (PreparedStatement ps =
                     cn.prepareStatement("SELECT COUNT(id) connection "
                             + "FROM events "
                             + "WHERE user = ? "
                             + "AND YEAR(date) = YEAR(CURDATE()) "
                             + "AND MONTH(date) = MONTH(CURDATE()) "
                             + "AND (action = 6 OR action = 2)")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    connection = rs.getInt("connection");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        LOG.debug("User connection " + id + " :" + connection);
        return connection == 2;
    }

    /**
     * Get IP Groups from abonent's subscribe
     *
     * @param id abonent
     * @return List ipGroups
     */
    public List<String> getIpGroupsForAbonent(int id) {
        List<String> ipGroups = new ArrayList<>();
        try (PreparedStatement ps =
                     cn.prepareStatement("SELECT DISTINCT INET_NTOA(c.ip) as ip "
                             + "FROM users u, packets_subscribe ps, packet_channel_links pcl, channels c "
                             + "WHERE u.id = ps.user "
                             + "AND ps.packet = pcl.packet "
                             + "AND pcl.channel = c.id "
                             + "AND u.id= ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ipGroups.add(rs.getString("ip"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return ipGroups;
    }

    /**
     * Set field cisco_job = 1 after profile update
     *
     * @param id
     */
    public void setCiscoJob(int id) {
        LOG.debug("Меняю статус executed в cisco_jobs на выполнен (1). UserID=" + id);
        try (PreparedStatement ps =
                     cn.prepareStatement("UPDATE cisco_jobs SET executed = 1 WHERE user = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Fina all abonents which have executed = 0 in cisco_jobs
     *
     * @return List<Abonent>
     */
    public List<Abonent> findAllOperAbonents() {
        List<Abonent> abonents = new ArrayList<>();
        String query = "select u.id user_id, u.full_name user_name,  u.port_number user_port, INET_NTOA(s.ip) switch_ip "
                + "from cisco_jobs cj, abonents a, users u, switches s "
                + "WHERE cj.executed = 0 "
                + "AND cj.user = a.id "
                + "AND a.id = u.id "
                + "AND u.switch = s.id "
                + "GROUP BY u.id";
        try (PreparedStatement ps = cn.prepareStatement(query)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Abonent abonent = new Abonent();
                    abonent.setUserId(rs.getInt("user_id"));
                    abonent.setUserName(rs.getString("user_name"));
                    abonent.setPort(rs.getInt("user_port"));
                    abonent.setSwitchIp(rs.getString("switch_ip"));
                    abonents.add(abonent);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return abonents;
    }


    @Override
    public void close() throws Exception {
        if (cn != null) {
            cn.close();
        }
    }
}
