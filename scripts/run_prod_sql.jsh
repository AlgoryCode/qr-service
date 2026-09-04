import java.sql.*;
import java.nio.file.*;
var c=DriverManager.getConnection(System.getenv("QR_DB_URL"),System.getenv("QR_DB_USER"),System.getenv("QR_DB_PASSWORD"));
c.setAutoCommit(false);
var sql=Files.readString(Path.of("scripts/order_ayaroof_drinks_after_desserts.sql"));
try { var s=c.createStatement(); s.execute(sql); c.commit(); System.out.println("SQL transaction committed"); }
catch(Exception e) { c.rollback(); throw e; }
var s=c.createStatement();
var rs=s.executeQuery("SELECT COUNT(*) FROM tbl_menu_products p JOIN tbl_menu_sub_category sc ON sc.id=p.sub_category_id JOIN tbl_menu_category c ON c.id=sc.menu_category_id WHERE p.menu_id=16 AND COALESCE(p.is_deleted,FALSE)=FALSE AND c.is_deleted=FALSE AND c.id<>39");
rs.next(); System.out.println("active products in product categories="+rs.getInt(1));
rs=s.executeQuery("SELECT COUNT(*) FROM tbl_menu_category WHERE menu_id=16 AND is_deleted=FALSE AND slug LIKE 'drink-%'"); rs.next(); System.out.println("active heading categories="+rs.getInt(1));
rs=s.executeQuery("SELECT COUNT(*) FROM tbl_menu_products p JOIN tbl_menu_sub_category sc ON sc.id=p.sub_category_id JOIN tbl_menu_category c ON c.id=sc.menu_category_id WHERE p.menu_id=16 AND p.product_id BETWEEN 723 AND 897 AND COALESCE(p.is_deleted,FALSE)=FALSE AND c.slug LIKE 'drink-%' AND c.is_deleted=FALSE"); rs.next(); System.out.println("drink products assigned to heading categories="+rs.getInt(1));
rs=s.executeQuery("SELECT COUNT(*) FROM tbl_menu_category WHERE menu_id=16 AND id=39 AND is_deleted=TRUE"); rs.next(); System.out.println("legacy drinks category soft-deleted="+rs.getInt(1));
c.close();
