package org.sandbag.eutlws;

import com.google.gson.Gson;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by root on 07/03/16.
 */
public class EUTLWebScraper {

    public static final String INSTALLATIONS_HEADER = "Country\tAccount Type\tAccount Holder Name\t" +
            "Company Registration Number\tAccount Status\tType\tCompany Name\tCompany Main Address\tCompany Secondary Address\t" +
            "Postal Code\tCompany City\tInstallation ID\tInstallation name\tPermit ID\tPermit Entry Date\t" +
            "Permit Expiry/Revocation Date\tSubsidiary Company\tParent Company\tE-PRTR identification\t" +
            "Installation Main Address\tInstallation Secondary Address\tInstallation Postal Code\tInstallation City\t" +
            "Country ID\tLatitude\tLongitude\tMain Activity";

    public static final String AIRCRAFT_OPERATORS_HEADER = "Country\tAccount Type\tAccount Holder Name\t" +
            "Company Registration Number\tAccount Status\tType\tCompany Name\tCompany Main Address\tCompany Secondary Address\t" +
            "Postal Code\tCompany City\tAircraft Operator ID\tUnique Code under Commission Regulation\tMonitoring Plan ID\t" +
            "Monitoring plan first year of applicability\tMonitoring plan year of expiry\tSubsidiary Company\t" +
            "Parent Company\tE-PRTR identification\tICAO designator\tAircraft Operator Main address\tAircraft Operator Secondary Address\t" +
            "Aircraft Operator Postal Code\tAircraft Operator City\tCountry ID\tLatitude\tLongitude\tMain Activity";

    public static final String ALLOWANCES_IN_ALLOCATION_TYPE = "Allowances in Allocation";
    public static final String VERIFIED_EMISSIONS_TYPE = "Verified Emissions";
    public static final String UNITS_SURRENDERED_TYPE = "Units Surrendered";
    public static final String COMPLIANCE_CODE_TYPE = "Compliance Code";

    public static final String NEW_ENTRANT_RESERVE_CODE = "*****";
    public static final String NEW_ENTRANT_RESERVE_CODE_REGEXP = "\\*\\*\\*\\*\\*";

    public static final String ARTICLE_10C_CODE = "****";
    public static final String ARTICLE_10C_CODE_REGEXP = "\\*\\*\\*\\*";

    public static final String INSTALLATIONS_COMPLIANCE_DATA_HEADER = "Country\tInstallation ID\tYear\t" +
            ALLOWANCES_IN_ALLOCATION_TYPE + "\t" + VERIFIED_EMISSIONS_TYPE + "\t" + UNITS_SURRENDERED_TYPE + "\t" +
            COMPLIANCE_CODE_TYPE;

    public static final String NER_ALLOCATION_DATA_HEADER = "Country\tInstallation ID\tYear\tNER allocation";
    public static final String ARTICLE_10C_ALLOCATION_DATA_HEADER = "Country\tInstallation ID\tYear\tArticle 10c allocation";

    public static final String OFFSET_ENTITLEMENTS_INSTALLATIONS_DATA_HEADER = "Country\tInstallation ID\tValue";
    public static final String OFFSET_ENTITLEMENTS_AIRCRAFT_OPERATORS_DATA_HEADER = "Country\tInstallation ID\tValue";

    public static final String OFFSETS_HEADER = "Country\tInstallation ID\tOrginating Registry\tUnit Type\tAmount\t" +
            "Original Commitment Period\tApplicable Commitment Period\tYear of Compliance\tLULUCF Activity\tProject identifier\t" +
            "Track\tExpiry Date";

    public static void main(String[] args) throws Exception {


        if (args.length != 1) {
            System.out.println("This program expects the following parameters: " +
                    "1. Config JSON file");
        } else {

            String configFileSt = args[0];

            Gson gson = new Gson();
            EUTLWebScraperConfig config = gson.fromJson(new BufferedReader(new FileReader(new File(configFileSt))), EUTLWebScraperConfig.class);

            //avoiding warning messages from WebDriver
            java.util.logging.Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(java.util.logging.Level.SEVERE);


            //+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
            //+++++++++++++++++++++++NER ALLOCATION FILE+++++++++++++++++++++++++++++++++++++++++++++

            File nerAllocationFile = new File(config.ner_file);
            nerAllocationFile.createNewFile();
            BufferedWriter nerAllocOutBuff = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(nerAllocationFile),"UTF-8"));
            nerAllocOutBuff.write(NER_ALLOCATION_DATA_HEADER + "\n");


            //+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
            //+++++++++++++++++++++++ARTICLE 10C FILE++++++++++++++++++++++++++++++++++++++++++++++++

            File article10cFile = new File(config.article10c_file);
            article10cFile.createNewFile();
            BufferedWriter article10cOutBuff = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(article10cFile),"UTF-8"));
            article10cOutBuff.write(ARTICLE_10C_ALLOCATION_DATA_HEADER + "\n");

            ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(config.number_of_threads);

            getOffsetEntitlements(config.installations_offset_entitlements_file,
                    config.aircraft_operators_offset_entitlements_file,
                    threadPoolExecutor);

            getOffsets(config.offsets_folder,
                    threadPoolExecutor,
                    config);

            getOperatorHoldingAccounts(config.installations_folder,
                    config.aircraft_operators_folder,
                    config.compliance_folder,
                    nerAllocOutBuff,
                    article10cOutBuff,
                    threadPoolExecutor,
                    config);

            System.out.println("Maximum threads inside pool " + threadPoolExecutor.getMaximumPoolSize());
            while (threadPoolExecutor.getActiveCount() > 0) {
                TimeUnit.SECONDS.sleep(30);
                System.out.println("Just woke up! ");
                System.out.println("threadPoolExecutor.getActiveCount() = " + threadPoolExecutor.getActiveCount());
            }
            threadPoolExecutor.shutdown();
            nerAllocOutBuff.close();
            article10cOutBuff.close();

        }

    }

    public static void getOffsetEntitlements(String installationsOffsetEntitlementsFSt,
                                             String aircraftOperatorsOffsetEntitlementsFileSt,
                                             ThreadPoolExecutor threadPoolExecutor) throws Exception {

        File installationsFile = new File(installationsOffsetEntitlementsFSt);
        installationsFile.createNewFile();
        BufferedWriter installationsBuff = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(installationsFile),"UTF-8"));
        installationsBuff.write(OFFSET_ENTITLEMENTS_INSTALLATIONS_DATA_HEADER + "\n");

        File aircrafOpsFile = new File(aircraftOperatorsOffsetEntitlementsFileSt);
        aircrafOpsFile.createNewFile();
        BufferedWriter aircraftOpsBuff = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(aircrafOpsFile),"UTF-8"));
        aircraftOpsBuff.write(OFFSET_ENTITLEMENTS_AIRCRAFT_OPERATORS_DATA_HEADER + "\n");

        // Lambda Runnable
        Runnable offsetEntitlementsRunnable = () -> {
            try {

                // Create a new instance of the Firefox driver
                WebDriver driver = new HtmlUnitDriver();
                WebDriverWait wait = new WebDriverWait(driver, 100);

                //driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);

                driver.get("http://ec.europa.eu/environment/ets/ice.do?languageCode=en");

                WebElement searchButton = driver.findElement(By.id("btnSearch"));
                wait.until(ExpectedConditions.elementToBeClickable(By.id("btnSearch")));
                searchButton.click();

                WebElement nextButton = driver.findElement(By.name("nextList"));

                boolean endReached = false;

                int offsetEntitlementsCounter = 0;

                while (!endReached) {


                    WebElement entitlements_table = driver.findElement(By.id("tblEntitlements"));
                    List<WebElement> tr_colletion = entitlements_table.findElements(By.xpath("id('tblEntitlements')/tbody/tr"));

                    for(int rowCounter=2; rowCounter<tr_colletion.size();rowCounter++){
                        List<WebElement> td_collection = tr_colletion.get(rowCounter).findElements(By.xpath("td"));
                        String countrySt = td_collection.get(0).getText().trim();
                        String entityTypeSt = td_collection.get(1).getText().trim();
                        String idSt = td_collection.get(3).getText().trim();
                        String valueSt = td_collection.get(4).getText().trim();

                        if(entityTypeSt.equals("Installation")){
                            installationsBuff.write(countrySt + "\t" + idSt + "\t" + valueSt + "\n");
                        }else if(entityTypeSt.equals("Aircraft Operator")){
                            aircraftOpsBuff.write(countrySt + "\t" + idSt + "\t" + valueSt + "\n");
                        }
                    }


                    if (nextButton.getAttribute("disabled") != null) {
                        endReached = true;
                    }

                    nextButton.click();
                    nextButton = driver.findElement(By.name("nextList"));

                    installationsBuff.flush();
                    aircraftOpsBuff.flush();

                    offsetEntitlementsCounter++;
                    if(offsetEntitlementsCounter % 10 == 0){
                        System.out.println(offsetEntitlementsCounter + " pages of offset entitlements already imported");
                    }

                }


                driver.quit();

                installationsBuff.close();
                aircraftOpsBuff.close();


            } catch (Exception e) {
                e.printStackTrace();
            }


        };

        threadPoolExecutor.submit(offsetEntitlementsRunnable);

    }


    public static void getOffsets(String offsetsFolderSt,
                                  ThreadPoolExecutor threadPoolExecutor,
                                  EUTLWebScraperConfig config) throws Exception {


        for (String countryCode : config.country_codes) {

            // Lambda Runnable
            Runnable countryRunnable = () -> {

                try {

                    System.out.println("(Offsets) " + Thread.currentThread().getName() + " is running");
                    System.out.println("(Offsets) countryCode = " + countryCode);

                    File offsetsDataFile = new File(offsetsFolderSt + "/" + countryCode + ".csv");
                    offsetsDataFile.createNewFile();
                    BufferedWriter offsetsBuff = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(offsetsDataFile),"UTF-8"));
                    offsetsBuff.write(OFFSETS_HEADER + "\n");

                    // Create a new instance of the Firefox driver
                    WebDriver driver = new HtmlUnitDriver();
                    WebDriverWait wait = new WebDriverWait(driver, 100);

                    //driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);

                    driver.get("http://ec.europa.eu/environment/ets/ohaDetails.do?permitIdentifier=&accountID=&form=oha&installationIdentifier=&complianceStatus=&account.registryCodes=" + countryCode + "&primaryAuthRep=&searchType=oha&identifierInReg=&mainActivityType=-1&buttonAction=select&account.registryCode=&languageCode=en&installationName=&accountHolder=&accountStatus=&accountType=&action=&registryCode=&periods.selectedItems=1&returnURL=");

                    WebElement nextButton;

                    boolean endReached = false;

                    int offsetsCounter = 0;

                    while (!endReached) {

                        String urlToGoBackTo = driver.getCurrentUrl();


                        //-------------------------------------------------------------------------
                        //------------------------GENERAL INFORMATION------------------------------

                        WebElement tableGeneralInfo = driver.findElement(By.id("tblAccountGeneralInfo"));
                        List<WebElement> tr_collection_account_general_info = tableGeneralInfo.findElements(By.xpath("id('tblAccountGeneralInfo')/tbody/tr"));

                        WebElement generalInfoRow = tr_collection_account_general_info.get(2);
                        List<WebElement> td_collection = generalInfoRow.findElements(By.xpath("td"));

                        String installationIdSt = td_collection.get(3).getText();

                        //System.out.println("installationIdSt = " + installationIdSt);

                        //===============================INFORMATION TABLE==========================
                        //=========================================================================

                        WebElement tableDetails = driver.findElement(By.id("tblChildDetails"));

                        List<WebElement> tr_collection_child_details = tableDetails.findElements(By.xpath("id('tblChildDetails')/tbody/tr/td/div/table/tbody/tr"));

                        //System.out.println("tr_collection.size() = " + tr_collection_child_details.size());

                        WebElement validRow = tr_collection_child_details.get(2);
                        List<WebElement> td_elements = validRow.findElements(By.xpath("td"));
                        WebElement detailsElement = td_elements.get(td_elements.size()-1);

                        String nextUrlValueSt = detailsElement.findElement(By.tagName("a")).getAttribute("href");
                        //System.out.println("nextUrlValueSt = " + nextUrlValueSt);
                        driver.get(nextUrlValueSt);

                        //********************************************************************
                        //*********************SURRENDERED UNITS TABLE**************************

                        boolean nextButtonFound = false;

                        //System.out.println("driver.getCurrentUrl() = " + driver.getCurrentUrl());

                        do{

                            WebElement surrenderedUnitsTable = driver.findElement(By.id("tblChildDetails"));

                            List<WebElement> rows = surrenderedUnitsTable.findElements(By.xpath("id('tblChildDetails')/tbody/tr/td/table/tbody/tr/td/div/table/tbody/tr"));

                            //System.out.println("rows.size() = " + rows.size());

                            List<WebElement> nextButtonSurrenderedUnitsList = surrenderedUnitsTable.findElements(By.xpath("id('tblChildDetails')/tbody/tr/td/table/tbody/tr/td/div/input[@value='Next>']"));
                            WebElement nextButtonSurrenderedUnits = null;

                            if(nextButtonSurrenderedUnitsList.size() > 0){
                                nextButtonSurrenderedUnits = nextButtonSurrenderedUnitsList.get(0);
                                nextButtonFound = true;
                                //System.out.println("NEXT BUTTON FOUND!");
                            }

                            for (int rowCounter=2;rowCounter<rows.size();rowCounter++){
                                WebElement currentRow = rows.get(rowCounter);
                                List<WebElement> columns = currentRow.findElements(By.xpath("td"));

                                String originatingRegistrySt = columns.get(0).getText().trim();
                                String unitTypeSt = columns.get(1).getText().trim();
                                String amountSt = columns.get(2).getText().trim();
                                String originalCommitmentPeriodSt = columns.get(3).getText().trim();
                                String applicableCommitmentPeriodSt = columns.get(4).getText().trim();
                                String yearForComplianceSt = columns.get(5).getText().trim();
                                String lulucfActivitySt = columns.get(6).getText().trim();
                                String projectIDst = columns.get(7).getText().trim();
                                String trackSt = columns.get(8).getText().trim();
                                String expiryDateSt = columns.get(9).getText().trim();

                                offsetsBuff.write(countryCode + "\t" + installationIdSt + "\t" + originatingRegistrySt + "\t" +
                                        unitTypeSt + "\t" + amountSt + "\t" + originalCommitmentPeriodSt + "\t" +
                                        applicableCommitmentPeriodSt + "\t" + yearForComplianceSt + "\t" +
                                        lulucfActivitySt + "\t" + projectIDst + "\t" + trackSt + "\t" + expiryDateSt + "\n");
                                //System.out.println("writing offset file..... " + countryCode);
                            }

                            offsetsBuff.flush();
                            //System.out.println(countryCode + " offsets file flushed!");

                            if(nextButtonFound){
                                if(nextButtonSurrenderedUnits.getAttribute("disabled") != null){
                                    //System.out.println("Next button is disabled... leaving the loop");
                                    break;
                                }else{
                                    //System.out.println("Clicking on next button!");
                                    nextButtonSurrenderedUnits.click();
                                }
                            }

                        }while(nextButtonFound);


                        driver.get(urlToGoBackTo);

                        nextButton = driver.findElement(By.name("nextList"));
                        wait.until(ExpectedConditions.visibilityOf(nextButton));

                        if (nextButton.getAttribute("disabled") != null) {
                            endReached = true;
                        }else{

                            wait.until(ExpectedConditions.elementToBeClickable(nextButton));
                            nextButton.click();
                        }


                        offsetsCounter++;
                        if(offsetsCounter % 20 == 0){
                            System.out.println(offsetsCounter + " offsets already retrieved for country: " + countryCode);
                        }
                    }


                    driver.quit();

                    offsetsBuff.close();


                } catch (Exception e) {
                    e.printStackTrace();
                }

            };

            threadPoolExecutor.submit(countryRunnable);
        }


    }



    public static void getOperatorHoldingAccounts(String installationsFolderSt,
                                                  String aircraftOpsFolderSt,
                                                  String complianceFolderSt,
                                                  BufferedWriter nerAllocOutBuff,
                                                  BufferedWriter article10cOutBuff,
                                                  ThreadPoolExecutor threadPoolExecutor,
                                                  EUTLWebScraperConfig config) throws Exception {


        for (String countryCode : config.country_codes) {

            // Lambda Runnable
            Runnable countryRunnable = () -> {

                try {

                    System.out.println("(OHAs) " + Thread.currentThread().getName() + " is running");
                    System.out.println("Get OHAs countryCode = " + countryCode);

                    File installationsFile = new File(installationsFolderSt + "/" + countryCode + ".csv");
                    installationsFile.createNewFile();
                    BufferedWriter installationsOutBuff = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(installationsFile),"UTF-8"));
                    installationsOutBuff.write(INSTALLATIONS_HEADER + "\n");

                    File aircraftOpsFile = new File(aircraftOpsFolderSt + "/" + countryCode + ".csv");
                    aircraftOpsFile.createNewFile();
                    BufferedWriter aircraftOpsOutBuff = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(aircraftOpsFile),"UTF-8"));
                    aircraftOpsOutBuff.write(AIRCRAFT_OPERATORS_HEADER + "\n");

                    File installationsCompDataFile = new File(complianceFolderSt + "/" + countryCode + ".csv");
                    installationsCompDataFile.createNewFile();
                    BufferedWriter installationsCompOutBuff = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(installationsCompDataFile),"UTF-8"));
                    installationsCompOutBuff.write(INSTALLATIONS_COMPLIANCE_DATA_HEADER + "\n");

                    // Create a new instance of the Firefox driver
                    WebDriver driver = new HtmlUnitDriver();
                    //driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);

                    driver.get("http://ec.europa.eu/environment/ets/oha.do?form=oha&languageCode=en&account.registryCodes=" + countryCode + "&accountHolder=&installationIdentifier=&installationName=&permitIdentifier=&mainActivityType=-1&search=Search&searchType=oha&currentSortSettings=&resultList.currentPageNumber=1");

                    WebElement detailsAllButton = driver.findElement(By.name("detailsAllAllPeriods"));
                    detailsAllButton.click();

                    WebElement nextButton = driver.findElement(By.name("nextList"));

                    boolean endReached = false;

                    int operatorHoldingAccountCounter = 0;

                    while (!endReached) {

                        //-------------------------------------------------------------------------
                        //------------------------GENERAL INFORMATION------------------------------

                        WebElement tableGeneralInfo = driver.findElement(By.id("tblAccountGeneralInfo"));
                        List<WebElement> tr_collection_account_general_info = tableGeneralInfo.findElements(By.xpath("id('tblAccountGeneralInfo')/tbody/tr"));

                        WebElement validRow = tr_collection_account_general_info.get(2);
                        List<WebElement> td_collection = validRow.findElements(By.xpath("td"));

                        String nationalAdministratorSt = td_collection.get(0).getText();
                        String accountTypeSt = td_collection.get(1).getText();
                        String accountHolderNameSt = td_collection.get(2).getText();
                        String installationIdSt = td_collection.get(3).getText();
                        String companyRegistrationNumberSt = td_collection.get(4).getText();
                        String accountStatus = td_collection.get(5).getText();


                        //====================DETAILS ON CONTACT INFORMATION================================
                        //================================================================================
                        WebElement tableContactInfo = driver.findElement(By.id("tblAccountContactInfo"));
                        List<WebElement> tr_collection_account_contact_info = tableContactInfo.findElements(By.xpath("id('tblAccountContactInfo')/tbody/tr"));

                        String companyTypeSt = "";
                        String companyNameSt = "";
                        String companyMainAddressSt = "";
                        String companySecondaryAddressSt = "";
                        String companyPostalCodeSt = "";
                        String companyCitySt = "";

                        if(tr_collection_account_contact_info.size() > 2) {
                            validRow = tr_collection_account_contact_info.get(2);
                            td_collection = validRow.findElements(By.xpath("td"));

                            companyTypeSt = td_collection.get(0).getText();
                            companyNameSt = td_collection.get(1).getText();
                            companyMainAddressSt = td_collection.get(2).getText();
                            companySecondaryAddressSt = td_collection.get(3).getText();
                            companyPostalCodeSt = td_collection.get(4).getText();
                            companyCitySt = td_collection.get(5).getText();
                            //String countryNameSt = td_collection.get(6).getText();
                        }

                        String contentToBeWrittenSt = nationalAdministratorSt + "\t" + accountTypeSt + "\t" + accountHolderNameSt + "\t" +
                                companyRegistrationNumberSt + "\t" + accountStatus + "\t";
                        contentToBeWrittenSt += companyTypeSt + "\t" + companyNameSt + "\t" + companyMainAddressSt + "\t"
                                + companySecondaryAddressSt + "\t" + companyPostalCodeSt + "\t" + companyCitySt + "\t";

                        //===============================INFORMATION TABLE==========================
                        //=========================================================================

                        WebElement tableDetails = driver.findElement(By.id("tblChildDetails"));
                        List<WebElement> table_collection = tableDetails.findElements(By.xpath("id('tblChildDetails')/tbody/tr/td/table"));

                        //+++++++++++++++++++GENERAL INFO++++++++++++++++++++++++

                        WebElement generalInformationTable = table_collection.get(0);
                        List<WebElement> tr_collection_general_info = generalInformationTable.findElements(By.xpath("tbody/tr"));

                        WebElement headerRow = tr_collection_general_info.get(1);
                        td_collection = headerRow.findElements(By.xpath("td"));
                        String isAircraftText = td_collection.get(0).getText().trim();

                        boolean isAircraft = isAircraftText.equals("Aircraft Operator ID");

                        //+++++++++++++++++++ADDRESS INFO++++++++++++++++++++++++

                        WebElement addressInformationTable = table_collection.get(1);
                        List<WebElement> tr_collection_address = addressInformationTable.findElements(By.xpath("tbody/tr"));

                        WebElement dataRow = tr_collection_address.get(2);
                        td_collection = dataRow.findElements(By.xpath("td"));

                        String installationMainAddressSt = td_collection.get(0).getText();
                        String installationSecondaryAddressSt = td_collection.get(1).getText();
                        String installationPostalCodeSt = td_collection.get(2).getText();
                        String installationCitySt = td_collection.get(3).getText();
                        String installationCountryId = td_collection.get(4).getText();
                        String installationLatitudeSt = td_collection.get(5).getText();
                        String installationLongitudeSt = td_collection.get(6).getText();
                        String installationMainActivitySt = td_collection.get(7).getText();

                        String addressInfoSt = installationMainAddressSt + "\t" + installationSecondaryAddressSt + "\t" +
                                installationPostalCodeSt + "\t" + installationCitySt + "\t" + installationCountryId + "\t" +
                                installationLatitudeSt + "\t" + installationLongitudeSt + "\t" + installationMainActivitySt + "\n";

                        if (isAircraft) {

                            aircraftOpsOutBuff.write(contentToBeWrittenSt);

                            dataRow = tr_collection_general_info.get(2);
                            td_collection = dataRow.findElements(By.xpath("td"));

                            String aircraftOpId = td_collection.get(0).getText();
                            String uniqueCodeComissionSt = td_collection.get(1).getText();
                            String monitoringPlanIDst = td_collection.get(2).getText();
                            String monitoringPlanFirstYearSt = td_collection.get(3).getText();
                            String monitoringPlanYearExpirySt = td_collection.get(4).getText();
                            String subsidiaryCompanySt = td_collection.get(5).getText();
                            String parentCompanySt = td_collection.get(6).getText();
                            String eprtrIdSt = td_collection.get(7).getText();
                            String icaoDesignatorSt = td_collection.get(8).getText();

                            aircraftOpsOutBuff.write(aircraftOpId + "\t" + uniqueCodeComissionSt + "\t" + monitoringPlanIDst +
                                    "\t" + monitoringPlanFirstYearSt + "\t" + monitoringPlanYearExpirySt + "\t" +
                                    subsidiaryCompanySt + "\t" + parentCompanySt + "\t" + eprtrIdSt + "\t" + icaoDesignatorSt + "\t");

                            aircraftOpsOutBuff.write(addressInfoSt);

                            aircraftOpsOutBuff.flush();


                        } else {

                            installationsOutBuff.write(contentToBeWrittenSt);

                            dataRow = tr_collection_general_info.get(2);
                            td_collection = dataRow.findElements(By.xpath("td"));

                            installationIdSt = td_collection.get(0).getText().trim();
                            String installationNameSt = td_collection.get(1).getText();
                            String permitIDSt = td_collection.get(2).getText();
                            String permitEntryDateSt = td_collection.get(3).getText();
                            String permitExpiryDateSt = td_collection.get(4).getText();
                            String subsidiaryCompanySt = td_collection.get(5).getText();
                            String parentCompanySt = td_collection.get(6).getText();
                            String eprtrIdSt = td_collection.get(7).getText();

                            installationsOutBuff.write(installationIdSt + "\t" + installationNameSt + "\t" + permitIDSt +
                                    "\t" + permitEntryDateSt + "\t" + permitExpiryDateSt + "\t" + subsidiaryCompanySt +
                                    "\t" + parentCompanySt + "\t" + eprtrIdSt + "\t");

                            installationsOutBuff.write(addressInfoSt);

                            installationsOutBuff.flush();

                        }


                        //*************************COMPLIANCE INFORMATION++++++++++++++++++++++++

                        List<WebElement> complianceRows = tableDetails.findElements(By.xpath("id('tblChildDetails')/tbody/tr/td/div/table/tbody/tr"));
                        for (int i = 2; i <= 17; i++) {
                            //System.out.println("i = " + i);
                            WebElement currentRow = complianceRows.get(i);
                            List<WebElement> columns = currentRow.findElements(By.xpath("td"));

                            String yearSt = columns.get(1).getText().trim();
                            String allowancesInAllocationSt = columns.get(2).getText().trim();
                            String verifiedEmissionsSt = columns.get(3).getText().trim().replaceAll("\n", " ");
                            String unitsSurrenderedSt = columns.get(4).getText().trim().replaceAll("\n", " ");
                            //String cumulativeSurrenderedUnitsSt = columns.get(5).getText();
                            //String cumulativeVerifiedEmissionsSt = columns.get(6).getText();
                            String complianceCodeSt = columns.get(7).getText().trim().replaceAll("\n", " ");

                            String[] newLineSplit = allowancesInAllocationSt.split("\n");

                            if (newLineSplit.length == 3) {

                                //-------------------NER ALLOCATIONS----------------------
                                String nerValue = newLineSplit[2].split(NEW_ENTRANT_RESERVE_CODE_REGEXP)[0].trim();
                                nerAllocOutBuff.write(countryCode + "\t" + installationIdSt + "\t" + yearSt + "\t" +
                                        nerValue + "\n");

                                //-------------------ARTICLE 10C ALLOCATIONS----------------------
                                String article10cValue = newLineSplit[1].split(ARTICLE_10C_CODE_REGEXP)[0].trim();
                                article10cOutBuff.write(countryCode + "\t" + installationIdSt + "\t" + yearSt + "\t" +
                                        article10cValue + "\n");

                                allowancesInAllocationSt = newLineSplit[0].trim();


                            } else if (newLineSplit.length == 2) {

                                String otherValue = newLineSplit[1];

                                if (otherValue.indexOf(NEW_ENTRANT_RESERVE_CODE) > 0) {
                                    //-------------------NER ALLOCATIONS----------------------
                                    String nerValue = otherValue.split(NEW_ENTRANT_RESERVE_CODE_REGEXP)[0].trim();
                                    nerAllocOutBuff.write(countryCode + "\t" + installationIdSt + "\t" + yearSt + "\t" +
                                            nerValue + "\n");

                                } else if (otherValue.indexOf(ARTICLE_10C_CODE) > 0) {
                                    //-------------------ARTICLE 10C ALLOCATIONS----------------------
                                    String article10cValue = otherValue.split(ARTICLE_10C_CODE_REGEXP)[0].trim();
                                    article10cOutBuff.write(countryCode + "\t" + installationIdSt + "\t" + yearSt + "\t" +
                                            article10cValue + "\n");
                                }

                                allowancesInAllocationSt = newLineSplit[0].trim();
                            }

                            if(!allowancesInAllocationSt.isEmpty() || !verifiedEmissionsSt.isEmpty() ||
                                    !unitsSurrenderedSt.isEmpty() || !complianceCodeSt.isEmpty()){


                                //------------------STANDARD ALLOCATIONS---------------------
                                installationsCompOutBuff.write(countryCode + "\t" + installationIdSt + "\t" + yearSt + "\t" +
                                        allowancesInAllocationSt + "\t" + verifiedEmissionsSt + "\t" + unitsSurrenderedSt + "\t" +
                                        complianceCodeSt + "\n");
                            }


                        }


                        installationsCompOutBuff.flush();

                        if (nextButton.getAttribute("disabled") != null) {
                            endReached = true;
                        }

                        nextButton.click();
                        nextButton = driver.findElement(By.name("nextList"));

                        operatorHoldingAccountCounter++;
                        if(operatorHoldingAccountCounter % 20 == 0){
                            System.out.println(operatorHoldingAccountCounter + " OHAs already retrieved for country: " + countryCode);
                        }

                    }


                    driver.quit();

                    installationsOutBuff.close();
                    aircraftOpsOutBuff.close();
                    installationsCompOutBuff.close();
                    nerAllocOutBuff.flush();
                    article10cOutBuff.flush();


                } catch (Exception e) {
                    e.printStackTrace();
                }

            };

            threadPoolExecutor.submit(countryRunnable);
        }



    }

}
