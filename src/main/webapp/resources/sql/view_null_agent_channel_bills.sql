-- select `ID`,`INSID`,`DEPTID`,`RETIRED`,`RETIRECOMMENTS` FROM bill b WHERE b.`BILLTYPE`='ChannelAgent' and b.`PAYMENTMETHOD`='Agent' and b.`CREDITCOMPANY_ID` is null;
select `ID`,`INSID`,`DEPTID`,`CREATEDAT`,`CREDITCOMPANY_ID`,`RETIRED`,`RETIRECOMMENTS` 
FROM bill b WHERE b.`BILLTYPE`='ChannelAgent' and b.`PAYMENTMETHOD`='Agent';