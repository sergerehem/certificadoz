import util.*

import com.google.appengine.api.blobstore.BlobKey 
import com.google.appengine.api.blobstore.BlobInfo
import com.google.appengine.api.files.FileReadChannel
//import java.nio.channels.Channels

if (!params.status) {
  request.status = "GETPDF"
  forward "/WEB-INF/pages/upload.gtpl"
} else {
  request.status = params.status
  switch (params.status) {
    case "GETCSV": 
      //PDF
      def blobs = blobstore.getUploadedBlobs(request)
      def pdfFile = blobs["pdfFile"]
      request.pdfKey = pdfFile.keyString
      request.pdfName = pdfFile.filename    
      pdfFile.withStream { inputStream -> 
        def pdf = new PDF()
        pdf.open(inputStream) 
        request.pdfFields = "\$" + pdf.listFormFields().inject() { s,e -> s += ", \$$e" }
      }
      forward "/WEB-INF/pages/upload.gtpl"
      break
    case "GETMSGDATA":
      //CSV
      def blobs = blobstore.getUploadedBlobs(request)
      def csvFile = blobs["csvFile"]
      request.pdfKey = params.pdfKey
      request.pdfName = params.pdfName
      request.pdfFields = params.pdfFields
      request.csvKey = csvFile.keyString      
      forward "/WEB-INF/pages/upload.gtpl"
      break
    case "PREVIEW":     
      def pdfFile = new BlobKey(params.pdfKey) 
      def csvFile = new BlobKey(params.csvKey)       
      def csvData = getCSVData(csvFile)         
      def outputPdfName = "preview.pdf"
      def pdfStamper = gerarPDF(pdfFile, csvData[0], outputPdfName)
      
      response.setHeader("Content-Type", "application/pdf");
      response.setHeader("Content-Length", String.valueOf(pdfStamper.blobKey.info.size));
      response.setHeader("Content-Disposition", "attachment;filename=\"$outputPdfName\"");
      blobstore.serve(pdfStamper.blobKey, response)	   
      break   
    default:
      def csvFile = new BlobKey(params.csvKey)          
      def csvData = getCSVData(csvFile)
      if (csvData.size <= 101) {
        def pdfFile = new BlobKey(params.pdfKey) 

        for (data in csvData) {
          def outputPdfName = "${data['email']}_${pdfFile.filename}"
          def pdfStamper = gerarPDF(pdfFile, data, outputPdfName)
          def outputPdfBytes = getBytes(pdfStamper) 

          //println "Enviando arquivo '$outputPdfName' para email '${data['email']}'<br/>"	    

          def subject = params.subject //evalScript(messageVars, params.subject)
          def message = evalScript(getMessageVars(pdfFile, data), params.message)

          Mail.send(params.fromEmail, params.fromName,data['email'],data['email'], subject, message, outputPdfName,outputPdfBytes)
          //println "$params.fromEmail $params.fromName $data['email'] $data['email'] $params.subject $message $outputPdfName"
          pdfStamper.delete()
        }
        pdfFile.delete()
        csvFile.delete()
        status = "OK"
        message = "$csvData.size certificados enviados por email com sucesso!"
      } else {
        status = "ERRO"
        message = "Na versão Beta não é possível enviar mais de 100 certificados. Caso deseje ampliar o limite, entre em contato com serge.rehem at gmail.com"
      }

      request.status = status      
      request.message = message
            
      forward "/WEB-INF/pages/success.gtpl"      
  }
}

def evalScript(vars, script) {
  Object evalScript = "${vars}return '''${script.trim()}'''"
  GroovyShell shell = new GroovyShell() 
  shell.evaluate(evalScript)
}

def gerarPDF(pdfFile, data, outputPdfName) {
  byte[] outputPdfBytes
  def pdfStamper
  String messageVars = ""
  pdfFile.withStream { inputStream -> 
  	def pdf = new PDF()
    pdf.open(inputStream) 
    pdfStamper = files.createNewBlobFile("application/pdf")
    pdfStamper.withOutputStream(locked: true, finalize: true) { outputStream ->
      pdf.preparePdfStamper(outputStream)
      pdf.listFormFields().each { fieldName ->
        pdf.changeFieldValue(fieldName, data[fieldName])
        messageVars += "$fieldName = \"${data[fieldName]}\";"
      }
      pdf.closeAll()
    }
  }
  pdfStamper
}

def getMessageVars(pdfFile, data) {
  String messageVars = ""
  pdfFile.withStream { inputStream -> 
  	def pdf = new PDF()
    pdf.open(inputStream)   
    pdf.listFormFields().each { fieldName ->
      messageVars += "$fieldName = \"${data[fieldName]}\";"
    }
    pdf.closePdf()
  }
  messageVars
}

def getBytes(file) {
  blobstore.fetchData(file.blobKey, 0, getSize(file)	- 1) 
}

def getSize(file) {
  file.blobKey.info.size
}

def getCSVData(csvFile) {
  def csvData = []
  csvFile.withStream { csvInputStream ->
    def fieldNames = []
    def f = 1 
    csvInputStream.splitEachLine(",") { fields ->
      if (f++==1) {
        fieldNames = fields      
      } else {
        def fieldsMap = [:]
        fieldNames.eachWithIndex { key, index ->
          fieldsMap[key] = fields[index]
        }
        csvData << fieldsMap
      }
    }
  }
  csvData
  //println "CSVDATA=$csvData<br/>"
}


/*
response.setHeader("Content-Type", "application/pdf");
response.setHeader("Content-Length", String.valueOf(inf.size));
response.setHeader("Content-Disposition", "attachment;filename=\"$outputPdfName\"");
blobstore.serve(pdfStamper.blobKey, response)	    
*/

