import java.sql.*;
var c=DriverManager.getConnection(System.getenv("QR_DB_URL"),System.getenv("QR_DB_USER"),System.getenv("QR_DB_PASSWORD"));
var s=c.createStatement();
var rs=s.executeQuery("SELECT id,email,first_name,last_name FROM tbl_user WHERE lower(email) LIKE '%ayaroof%' OR lower(first_name) LIKE '%ayaroof%' OR lower(last_name) LIKE '%ayaroof%'");
var md=rs.getMetaData(); while(rs.next()){for(int i=1;i<=md.getColumnCount();i++)System.out.print(md.getColumnName(i)+"="+rs.getString(i)+" ");System.out.println();}
System.out.println("-- menus --");
rs=s.executeQuery("SELECT m.menu_id,m.qr_id,m.user_id,m.business_name,m.active,m.is_deleted FROM tbl_menu m JOIN tbl_user u ON u.id=m.user_id WHERE lower(u.email) LIKE '%ayaroof%' OR lower(u.first_name) LIKE '%ayaroof%' OR lower(u.last_name) LIKE '%ayaroof%' ORDER BY m.menu_id");
md=rs.getMetaData(); while(rs.next()){for(int i=1;i<=md.getColumnCount();i++)System.out.print(md.getColumnName(i)+"="+rs.getString(i)+" ");System.out.println();}
System.out.println("-- existing taxonomy/products --");
rs=s.executeQuery("SELECT c.id,c.name,sc.id,sc.name,p.product_id,p.name,p.price,p.currency FROM tbl_menu_category c JOIN tbl_menu_sub_category sc ON sc.menu_category_id=c.id LEFT JOIN tbl_menu_products p ON p.sub_category_id=sc.id AND p.is_deleted=false WHERE c.menu_id=16 AND c.is_deleted=false ORDER BY c.sort_order,sc.sort_order,p.product_id");
md=rs.getMetaData(); while(rs.next()){for(int i=1;i<=md.getColumnCount();i++)System.out.print(md.getColumnName(i)+"="+rs.getString(i)+" ");System.out.println();}
c.close();
