$(() => {
    $('#drawChart').click(e => {
        e.preventDefault()
        ajaxPost()
    })

    function ajaxPost() {
        var chartZone = document.getElementById('chart')
        var form = document.getElementById('chartForm')
        var formData = new FormData(form)
        $.ajax({
            type:'POST',
            contentType: false,
            url: 'http://localhost:3000/get_chart_data',
            data: formData,
            processData: false,
            success: dados => drawCanvas(dados, chartZone),
            error: e => alert('Erro ao desenhar gráfico: ' + e)
        })
        $('#ipAgente').val('')
        $('#portaAgente').val('')
        $('#data').val('')
        $('#ifIndex').val('')
    }

    function drawCanvas(dados, chartId) {
            new Chart(chartId, {
                type: 'bar',
                data: {
                    labels: dados.labels,
                    datasets : [
                        {
                            label: dados.label,
                            data: dados.resultados
                        }
                    ]
                },
                options: {
                    legend: {display: false},
                    title: {
                        display:true,
                        text: 'Gráfico de informação do agente ' + dados.agente
                    }
                }
            })
    }
        
})