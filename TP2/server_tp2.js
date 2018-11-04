var snmp = require('net-snmp')
var http = require('http')
var express = require('express')
var pug = require('pug')
var fs = require('fs')

var app = express()
var number_interfaces = 0
var returnValues = []
var returnValue = {ifDescr:"",PhysAddress:"",inOctets:"",outOctets:"",diferenca:""}

app.get('/w3.css',(req,res)=> {
    res.writeHead(200,{'Content-Type':'text/css'})
    fs.readFile('stylesheets/w3.css',(erro,dados) => {
        if(!erro) res.write(dados)
        else console.log('erro')
        res.end()
    })
})

app.get('/',(req,res) => {
    var session = snmp.createSession('localhost','public')
    getNumberInterfaces(session)
    setTimeout(getSnmpVariables,100,session)
    console.log(JSON.stringify(returnValues))
    setTimeout(writeHTML, 500,res);
    
    

    console.log(returnValues)

})

function writeHTML(res) {
    //returnValue['diferenca'] = (parseInt(returnValue['inOctets']) - parseInt(returnValue['outOctets'])).toString()
    //console.log(returnValues)
    res.writeHead(200,{'Content-Type': 'text/html; charset=utf-8'})
    //console.log(JSON.stringify(returnValues))
    res.write(pug.renderFile('homepage.pug',{lista: returnValues}))
    res.end() 
}

function getSnmpVariables(session) {

    console.log('EEEEEEEE: ' + number_interfaces)
    for(var i = 1; i< number_interfaces+1;i++) {
        
        getIndividualInterface(session,i)
        returnValues.push(returnValue)
    }
}


function getIndividualInterface(session,i) {
    var oid = ["1.3.6.1.2.1.2.2.1.2." + i]
        session.get(oid, (error,varbinds) => {
            if(error) console.log('Fail: ' + error)
            else {
                returnValue['ifDescr'] = varbinds[0].value.toString()
                //console.log(JSON.stringify(varbinds[0]))
            }
        } )

        oid = ["1.3.6.1.2.1.2.2.1.6." + i]
        session.get(oid, (error,varbinds) => {
            if(error) console.log('Fail: ' + error)
            else {
                var data = JSON.parse(JSON.stringify(varbinds[0].value))
                var str = ''
                for(var i = 0; i < data.data.length; i++) {
                    if(i == (data.data.length - 1)) {
                        str += data.data[i].toString(16)
                    } else {
                        str += data.data[i].toString(16) + ':' 
                    }   
                }
                console.log(str)
                returnValue['PhysAddress'] = str
            }
        } )

        oid = ["1.3.6.1.2.1.2.2.1.10." + i]
        session.get(oid, (error,varbinds) => {
            if(error) console.log('Fail: ' + error)
            else {
                returnValue['inOctets'] = varbinds[0].value.toString()
                //console.log(varbinds[0].value.toString())      
            }
        } )

        oid = ["1.3.6.1.2.1.2.2.1.16." + i]
        session.get(oid, (error,varbinds) => {
            if(error) console.log('Fail: ' + error)
            else {
                returnValue['outOctets'] = varbinds[0].value.toString()
                //console.log(varbinds[0].value.toString())       
            }
        } )

}

function getNumberInterfaces(session) {
    var oid = ["1.3.6.1.2.1.2.1.0"] 
    session.get(oid,(error,varbinds) => {
        if(error) console.log('Fail: ' + error)
        else {
            number_interfaces = parseInt(varbinds[0].value)
        }
    })
}

var myServer = http.createServer(app)

myServer.listen(2000,() => {
    console.log('Servidor à escuta na porta 2000...')
})
